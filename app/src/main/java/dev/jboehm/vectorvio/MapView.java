package dev.jboehm.vectorvio;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Offline orbit viewer of saved metric snapshots, with independent segment selection. */
final class MapView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MapArchive archive;
    private final ArrayList<MapArchive.Keyframe> selected=new ArrayList<>();
    private final ArrayList<Integer> segments=new ArrayList<>();
    private int segmentIndex=0,keyframe=0;
    private float yaw=.5f,pitch=.5f,zoom=1,lastX,lastY;
    private float cx,cy,cz,extent=1;
    boolean showPoints=true,showPath=true;
    private final ScaleGestureDetector pinch;
    MapView(Context context,MapArchive archive) {
        super(context);this.archive=archive;setBackgroundColor(0xFF06121F);
        for(MapArchive.Keyframe k:archive.frames)if(!segments.contains(k.segment))segments.add(k.segment);
        selectSegment();
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.15f,Math.min(12,zoom*d.getScaleFactor()));invalidate();return true;}
        });
        setContentDescription("3D map. Drag to orbit, pinch to zoom. Each tracking segment has independent axes.");
    }
    int segment(){return segments.isEmpty()?0:segments.get(segmentIndex);}
    int count(){return selected.size();}
    void nextSegment(){if(!segments.isEmpty()){segmentIndex=(segmentIndex+1)%segments.size();selectSegment();invalidate();}}
    void setKeyframe(int index){keyframe=Math.max(0,Math.min(selected.size()-1,index));invalidate();}
    byte[] image(){return selected.isEmpty()?new byte[0]:selected.get(keyframe).image;}
    private void selectSegment() {
        selected.clear();for(MapArchive.Keyframe k:archive.frames)if(k.segment==segment())selected.add(k);
        keyframe=0;zoom=1;
        float lx=Float.MAX_VALUE,ly=lx,lz=lx,hx=-lx,hy=hx,hz=hx;
        for(MapArchive.Keyframe k:selected) {
            lx=Math.min(lx,k.pose[0]);hx=Math.max(hx,k.pose[0]);ly=Math.min(ly,k.pose[1]);hy=Math.max(hy,k.pose[1]);lz=Math.min(lz,k.pose[2]);hz=Math.max(hz,k.pose[2]);
            for(int i=0;i<k.points.length;i+=4){lx=Math.min(lx,k.points[i]);hx=Math.max(hx,k.points[i]);ly=Math.min(ly,k.points[i+1]);hy=Math.max(hy,k.points[i+1]);lz=Math.min(lz,k.points[i+2]);hz=Math.max(hz,k.points[i+2]);}
        }
        if(selected.isEmpty()){cx=cy=cz=0;extent=1;return;}
        cx=(lx+hx)/2;cy=(ly+hy)/2;cz=(lz+hz)/2;extent=Math.max(1,Math.max(hx-lx,Math.max(hy-ly,hz-lz)));
    }
    private float[] project(float x,float y,float z) {
        x-=cx;y-=cy;z-=cz;
        float xx=(float)(Math.cos(yaw)*x-Math.sin(yaw)*z),zz=(float)(Math.sin(yaw)*x+Math.cos(yaw)*z);
        float yy=(float)(Math.cos(pitch)*y-Math.sin(pitch)*zz);
        float depth=(float)(Math.sin(pitch)*y+Math.cos(pitch)*zz);
        float factor=3*extent/(3*extent+depth);
        float scale=Math.min(getWidth(),getHeight())*.48f/extent*zoom;
        return new float[]{getWidth()*.5f+xx*scale*factor,getHeight()*.5f-yy*scale*factor};
    }
    private void line(Canvas c,float[] a,float[] b,int color,float width) {
        paint.setColor(color);paint.setStrokeWidth(width*getResources().getDisplayMetrics().density);
        c.drawLine(a[0],a[1],b[0],b[1],paint);
    }
    private void text(Canvas c,String value,float y,int color) {
        paint.setColor(color);paint.setTextSize(12*getResources().getDisplayMetrics().density);
        c.drawText(value,18*getResources().getDisplayMetrics().density,y,paint);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);float dp=getResources().getDisplayMetrics().density;
        float gridStep=(float)Math.pow(10,Math.floor(Math.log10(extent/5)));
        for(int i=-10;i<=10;i++) {
            float value=i*gridStep;
            line(c,project(cx-10*gridStep,cy,cz+value),project(cx+10*gridStep,cy,cz+value),0x182F829B,.6f);
            line(c,project(cx+value,cy,cz-10*gridStep),project(cx+value,cy,cz+10*gridStep),0x182F829B,.6f);
        }
        int total=0;for(MapArchive.Keyframe k:selected)total+=k.points.length/4;
        int stride=Math.max(1,(total+9999)/10000),n=0;
        if(showPoints)for(MapArchive.Keyframe k:selected)for(int i=0;i<k.points.length;i+=4) {
            if(n++%stride!=0)continue;
            float[] p=project(k.points[i],k.points[i+1],k.points[i+2]);
            float quality=Math.max(0,Math.min(1,k.points[i+3]));
            paint.setColor(Color.argb((int)(70+185*quality),130,223,210));
            c.drawCircle(p[0],p[1],1.4f*dp,paint);
        }
        float[] previous=null;
        if(showPath)for(MapArchive.Keyframe k:selected) {
            float[] p=project(k.pose[0],k.pose[1],k.pose[2]);
            if(previous!=null){line(c,previous,p,0x405BF3FF,7);line(c,previous,p,0xFF85EDFF,2);}
            previous=p;paint.setColor(0xFFFFCE86);c.drawCircle(p[0],p[1],2.3f*dp,paint);
        }
        for(MapArchive.Poi poi:archive.pois)if(poi.segment==segment()) {
            float[] marker=project(poi.pose[0],poi.pose[1],poi.pose[2]);
            paint.setColor(0xFFFFC976);c.drawCircle(marker[0],marker[1],5*dp,paint);
            paint.setColor(Color.WHITE);paint.setTextSize(10*dp);
            c.drawText(poi.type+" · "+poi.label,marker[0]+7*dp,marker[1]-7*dp,paint);
        }
        if(!selected.isEmpty()) {
            MapArchive.Keyframe k=selected.get(keyframe);float[] p=project(k.pose[0],k.pose[1],k.pose[2]);
            paint.setColor(Color.WHITE);c.drawCircle(p[0],p[1],5*dp,paint);
            // Camera local -Z forward, rotated by saved quaternion, drawn as a direction arrow.
            float qx=k.pose[3],qy=k.pose[4],qz=k.pose[5],qw=k.pose[6],length=extent*.07f;
            float[] tip=project(k.pose[0]-2*(qx*qz+qw*qy)*length,k.pose[1]-2*(qy*qz-qw*qx)*length,k.pose[2]-(1-2*(qx*qx+qy*qy))*length);
            line(c,p,tip,0xFFFFFFFF,2);
        }
        int markCount=0;for(MapArchive.Poi poi:archive.pois)if(poi.segment==segment())markCount++;
        text(c,"SEGMENT "+segment()+"  /  "+selected.size()+" KEYFRAMES  /  "+total+" SAMPLES  /  "+markCount+" MARKERS",28*dp,0xFF78F4DC);
        text(c,"Drag to orbit · pinch to zoom · independent local world",48*dp,0xFF9FB7C8);
        if(archive.manualLoopConstraint)text(c,"MANUAL LOOP CONSTRAINT · assumed endpoints",68*dp,0xFFFFCB88);
        if(!selected.isEmpty()) {
            MapArchive.Keyframe k=selected.get(keyframe);
            text(c,String.format(Locale.US,"CAMERA XYZ  %+.2f  %+.2f  %+.2f m",k.pose[0],k.pose[1],k.pose[2]),(archive.manualLoopConstraint?88:68)*dp,0xFFD5E7EF);
        }
        text(c,String.format(Locale.US,"Grid %.2g m  ·  sparse snapshot, no surface mesh",gridStep),getHeight()-18*dp,0xFF9FB7C8);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE && e.getPointerCount()==1 && !pinch.isInProgress()) {
            yaw+=(e.getX()-lastX)*.008f;pitch=Math.max(-1.4f,Math.min(1.4f,pitch+(e.getY()-lastY)*.008f));invalidate();
        }
        lastX=e.getX();lastY=e.getY();return true;
    }
}
