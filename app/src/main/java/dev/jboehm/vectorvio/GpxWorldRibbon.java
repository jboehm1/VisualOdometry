package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.List;

/** Display-only: solid ribbons use observed floor; dashed ribbons use a virtual flat plane. */
final class GpxWorldRibbon {
    static final int MAX_PIECES=1600;
    static final double STEP_METERS=.9;
    static final class Result {
        final List<float[]> observed=new ArrayList<>();
        final List<float[]> estimated=new ArrayList<>();
        final float estimatedY;
        Result(float estimatedY){this.estimatedY=estimatedY;}
    }
    private int trackingSegment=-1;
    private float referenceY=Float.NaN;
    void reset(){trackingSegment=-1;referenceY=Float.NaN;}
    Result project(List<double[]> edges,float cameraX,float cameraY,float cameraZ,
                   List<GroundTrail.Floor> floors,int segment,boolean allowEstimated){
        if(segment!=trackingSegment){trackingSegment=segment;referenceY=Float.NaN;}
        float underCamera=GroundTrail.floorY(cameraX,cameraY,cameraZ,floors);
        if(!Float.isFinite(referenceY))
            referenceY=Float.isFinite(underCamera)?underCamera:cameraY-1.5f;
        else if(Float.isFinite(underCamera)&&Math.abs(underCamera-referenceY)<1.2f)
            referenceY=.97f*referenceY+.03f*underCamera;
        Result result=new Result(referenceY);
        int piece=0;
        for(double[] e:edges){
            if(e==null||e.length!=4)continue;
            boolean finite=true;for(double val:e)finite &=Double.isFinite(val);
            if(!finite)continue;
            double length=Math.hypot(e[2]-e[0],e[3]-e[1]);
            if(length<1e-5||length>61)continue;
            int slices=Math.max(1,(int)Math.ceil(length/STEP_METERS));
            for(int i=0;i<slices&&piece<MAX_PIECES;i++,piece++){
                double t0=(double)i/slices,t1=(double)(i+1)/slices;
                double x0=e[0]+t0*(e[2]-e[0]),z0=e[1]+t0*(e[3]-e[1]);
                double x1=e[0]+t1*(e[2]-e[0]),z1=e[1]+t1*(e[3]-e[1]);
                float y0=GroundTrail.floorY(x0,cameraY,z0,floors);
                float y1=GroundTrail.floorY(x1,cameraY,z1,floors);
                boolean observed=false;
                if(Float.isFinite(y0)&&Float.isFinite(y1))for(GroundTrail.Floor f:floors){
                    float a=GroundTrail.floorY(x0,cameraY,z0,f);
                    float b=GroundTrail.floorY(x1,cameraY,z1,f);
                    if(Float.isFinite(a)&&Float.isFinite(b)&&
                        Math.abs(a-y0)<.001f&&Math.abs(b-y1)<.001f){
                        result.observed.add(new float[]{(float)x0,a+.035f,(float)z0,
                            (float)x1,b+.035f,(float)z1});
                        observed=true;break;
                    }
                }
                // Virtual segments are deliberately dotted and must never be
                // confused with observed terrain or used to alter odometry.
                if(!observed&&allowEstimated&&piece%2==0){
                    float h=referenceY+.05f;
                    result.estimated.add(new float[]{(float)x0,h,(float)z0,(float)x1,h,(float)z1});
                }
            }
            if(piece>=MAX_PIECES)break;
        }
        return result;
    }
}
