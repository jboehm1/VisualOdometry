package dev.jboehm.vectorvio;

import com.google.ar.core.*;

/** GL-thread owner of one nearby metric reference. Never falls back to cross-frame
 * raw world differencing. Raw capture remains independent of this measurement. */
final class AnchoredOdometry {
    // Reference proximity, NOT a minimum measured displacement. Google advises <8m.
    private static final float REFERENCE_RADIUS_METERS=4f;
    private final AnchorCoordinates coordinates=new AnchorCoordinates();
    interface Reference {
        Pose pose();
        boolean tracking();
        void detach();
    }
    interface Factory { Reference create(Pose pose); }
    private Reference anchor;
    private long lastTimestamp;
    private int referenceId;
    Pose anchorPose,metricPose;
    boolean freshReference;
    int referenceId(){return referenceId;}
    Pose worldFromMetric(){return anchorPose==null?null:coordinates.worldFromMetric(anchorPose);}
    void reset(){
        if(anchor!=null){try{anchor.detach();}catch(RuntimeException ignored){}anchor=null;}
        coordinates.reset();lastTimestamp=0;anchorPose=metricPose=null;
    }
    Pose update(Session session,Pose camera,long timestamp){
        return update(p->{Anchor a=session.createAnchor(p);return new Reference(){
            public Pose pose(){return a.getPose();}
            public boolean tracking(){return a.getTrackingState()==TrackingState.TRACKING;}
            public void detach(){a.detach();}
        };},camera,timestamp);
    }
    Pose update(Factory factory,Pose camera,long timestamp){
        if(timestamp<=0)return null;
        if(lastTimestamp>0&&timestamp<=lastTimestamp)return null;
        if(lastTimestamp>0&&(timestamp-lastTimestamp)*1e-9>Odometer.MAX_GAP_SECONDS)reset();
        freshReference=false;
        if(anchor!=null&&!anchor.tracking())reset();
        if(anchor==null){
            // Identity orientation starts the metric axes with ARCore's gravity-up Y.
            anchor=factory.create(Pose.makeTranslation(camera.getTranslation()));
            coordinates.reset();referenceId++;freshReference=true;
        }
        lastTimestamp=timestamp;
        if(!anchor.tracking())return null;
        anchorPose=anchor.pose();
        metricPose=coordinates.camera(anchorPose,camera);
        float dx=camera.tx()-anchorPose.tx(),dy=camera.ty()-anchorPose.ty(),dz=camera.tz()-anchorPose.tz();
        if(dx*dx+dy*dy+dz*dz>REFERENCE_RADIUS_METERS*REFERENCE_RADIUS_METERS){
            // Prepare before detaching: on failure keep the still-valid old reference.
            Reference replacement=null;
            try{
                replacement=factory.create(Pose.makeTranslation(camera.getTranslation()));
                if(replacement.tracking()){
                    Pose newPose=replacement.pose();
                    coordinates.rebase(anchorPose,newPose);
                    Reference old=anchor;anchor=replacement;replacement=null;
                    anchorPose=newPose;referenceId++;
                    old.detach();
                }
            }catch(RuntimeException ignored){
                // A failed rebase must not interrupt camera/video or use raw pose deltas.
            }finally{if(replacement!=null)replacement.detach();}
        }
        return metricPose;
    }
}
