package dev.jboehm.vectorvio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;
import java.util.Locale;

/**
 * Custom real-time visual layer, drawn ABOVE the ARCore camera texture.
 * Reticle and grid are graphical aids, NOT tracked real-world landmarks.
 * Local bird's-eye path is projection of ARCore world X/Z; Y means vertical.
 */
public final class HudView extends View {
    private static final int INK = 0xFFF1FAFF;
    private static final int MUTED = 0xFF96AAB8;
    private static final int CYAN = 0xFF74FCE1;
    private static final int AMBER = 0xFFFFC976;
    private static final int SLATE = 0xFF1E3441;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Odometer.Snapshot snapshot;
    private String trackingText = "INITIALIZING";
    private String notice = "Move slowly and scan a textured environment";
    private int markers = 0;
    private String guideText="";
    public void updateGuide(String text){guideText=text;invalidate();}
    private int keyframes=0,photos=0,poiCount=0;
    private boolean mapFull=false;
    private String captureStatus="VIDEO OFF",gpsStatus="GPS OFF";
    public void updateDataStatus(String capture,String gps){captureStatus=capture;gpsStatus=gps;invalidate();}
    public void updateCapture(int frames,int images,boolean full){mapFull=full;updateCapture(frames,images,poiCount);}
    public void updateCapture(int frames,int images,int markers,boolean full){mapFull=full;updateCapture(frames,images,markers);}
    public void updateCapture(int keyframes,int photos){updateCapture(keyframes,photos,poiCount);}
    public void updateCapture(int frames,int images,int markers){this.keyframes=frames;this.photos=images;poiCount=markers;invalidate();}
    private SceneMapper.Snapshot scene;
    private float[] imu=new float[8];
    private float cameraHz;
    private volatile int layers=0;
    private volatile boolean worldGpu;
    private String rendererLabel="CANVAS";
    public void setRendererLabel(String label){rendererLabel=label;invalidate();}
    public void setWorldGpu(boolean enabled){worldGpu=enabled;postInvalidate();}
    private boolean sensors=false, expandedMap=false;
    private final float[] accelerationHistory=new float[90];
    private int historyIndex=0;
    public void cycleLayers(){ layers=(layers+1)%4;invalidate(); }
    public void setLayers(int value){layers=Math.max(0,Math.min(3,value));invalidate();}
    public int layers(){return layers;}
    public void toggleSensors(){sensors=!sensors;invalidate();}
    public void toggleMap(){expandedMap=!expandedMap;invalidate();}
    public void updateScene(SceneMapper.Snapshot value,float[] sensorValues,float hz) {
        scene=value;imu=sensorValues;cameraHz=hz;
        accelerationHistory[historyIndex++%90]=(float)Math.sqrt(imu[0]*imu[0]+imu[1]*imu[1]+imu[2]*imu[2]);
        invalidate();
    }
    private final float dp;

    public HudView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
    }
    private float d(float value) { return value*dp; }

    public void update(Odometer.Snapshot data, String tracking, String message, int markerCount) {
        this.snapshot = data;
        this.trackingText = tracking;
        this.notice = message;
        this.markers = markerCount;
        invalidate();
    }
    private void resetPaint() {
        p.reset();
        p.setAntiAlias(true);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }
    private void label(Canvas c, String text, float x, float y, float sp, int color, boolean bold) {
        resetPaint();
        p.setColor(color);
        p.setTextSize(d(sp));
        p.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(text, x, y, p);
    }
    private void box(Canvas c, float l, float t, float right, float bottom, float radius,
                     int color, int stroke) {
        resetPaint();
        r.set(l,t,right,bottom);
        p.setColor(color);
        c.drawRoundRect(r,d(radius),d(radius),p);
        if(stroke!=0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d(1));
            p.setColor(stroke);
            c.drawRoundRect(r,d(radius),d(radius),p);
            p.setStyle(Paint.Style.FILL);
        }
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width) {
        resetPaint(); p.setStrokeWidth(d(width)); p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND); p.setColor(color);
        c.drawLine(x1,y1,x2,y2,p);
    }
    private void disc(Canvas c,float x,float y,float radius,int color,boolean stroke) {
        resetPaint(); p.setColor(color);
        if(stroke){ p.setStrokeWidth(d(1.25f)); p.setStyle(Paint.Style.STROKE); }
        c.drawCircle(x,y,radius,p);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0) return;
        Odometer.Snapshot s=snapshot;

        drawScene(c,w,h);
        drawReticle(c,w,h);
        box(c,0,0,w,d(64),0,0x8507121D,0);
        label(c,"VECTOR",d(14),d(28),17,INK,true);
        label(c,trackingText,w-d(125),d(27),10,s!=null&&s.tracking?CYAN:AMBER,true);
        label(c,gpsStatus+" · "+captureStatus,d(14),d(49),9,MUTED,false);
        label(c,String.format(Locale.US,"%d KF · %d POI · %s · POSE %.0f Hz",keyframes,poiCount,rendererLabel,cameraHz),d(14),d(130),9,MUTED,false);
        if(scene!=null)label(c,"PATH · "+(scene.groundEstimated?"FLOOR EST.":scene.groundMissing?(scene.groundPoints>0?"FLOOR PARTIAL":"NO FLOOR"):"FLOOR"),w-d(135),d(130),9,0xFFFFAB57,true);
        if(expandedMap)drawLiveMap(c,d(12),d(143),w-d(24),Math.max(d(80),h-d(315)),s);
        else if(sensors)drawSensors(c,d(12),h-d(334),w-d(24),d(168));
        else drawMiniMap(c,w-d(118),h-d(280),d(106),d(112),s);
        drawDataPanel(c,w,h,s);
    }

    private void drawScene(Canvas c,int w,int h) {
        if(scene==null)return;
        c.save();c.clipRect(0,d(138),w,h-d(166));
        for(SceneMapper.Shape shape:scene.shapes) {
            if(worldGpu&&(shape.kind<4||shape.kind==5))continue;
            if(shape.kind<2 && (layers==2 || layers==3))continue;
            if(shape.kind==2 && (layers==1 || layers==3))continue;
            float[] xy=shape.xy;
            if(xy==null||xy.length%2!=0||((shape.kind==2||shape.kind==4||shape.kind==6)&&xy.length<2)||((shape.kind==3||shape.kind==5)&&xy.length<4)||(shape.kind<2&&xy.length<6))continue;
            int color=shape.kind==1?0xFFB5A0FF:CYAN;
            if(shape.kind==2){disc(c,xy[0]*w,xy[1]*h,d(1.2f),0x70F8D58A,false);continue;}
            if(shape.kind==5){
                line(c,xy[0]*w,xy[1]*h,xy[2]*w,xy[3]*h,0x5059B8FF,8);
                line(c,xy[0]*w,xy[1]*h,xy[2]*w,xy[3]*h,0xCC59B8FF,3);continue;
            }
            if(shape.kind==3){
                line(c,xy[0]*w,xy[1]*h,xy[2]*w,xy[3]*h,0x50FF922B,8);
                line(c,xy[0]*w,xy[1]*h,xy[2]*w,xy[3]*h,0xFFFFAB57,3);continue;
            }
            if(shape.kind==6){
                float x=xy[0]*w,y=xy[1]*h;disc(c,x,y,d(4),0xCC59B8FF,false);
                float labelX=Math.max(d(55),Math.min(w-d(80),x+d(8)));
                label(c,shape.label,labelX,Math.max(d(155),Math.min(h-d(170),y-d(7))),9,0xFF85C8FF,true);continue;
            }
            if(shape.kind==4) {
                float x=xy[0]*w,y=xy[1]*h;disc(c,x,y,d(7),0xFFFFC976,false);disc(c,x,y,d(2),Color.WHITE,false);
                float labelX=Math.max(d(92),Math.min(w-d(92),x));float labelY=Math.max(d(267),Math.min(h-d(324),y-d(14)));
                box(c,labelX-d(86),labelY-d(15),labelX+d(86),labelY+d(8),7,0xE7071925,0xAAFFC976);
                label(c,shape.label.length()>26?shape.label.substring(0,25)+"…":shape.label,labelX-d(80),labelY,9,AMBER,true);continue;
            }
            Path path=new Path();float cx=0,cy=0;
            for(int i=0;i<xy.length;i+=2) {
                float x=xy[i]*w,y=xy[i+1]*h;cx+=x;cy+=y;
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();resetPaint();p.setColor((color&0xFFFFFF)|0x14000000);c.drawPath(path,p);
        }
        if(Float.isFinite(scene.gpxDirectionRadians)){
            float x=w*.5f,y=d(190);c.save();c.rotate((float)Math.toDegrees(scene.gpxDirectionRadians),x,y);
            Path arrow=new Path();arrow.moveTo(x,y-d(13));arrow.lineTo(x-d(8),y+d(8));arrow.lineTo(x,y+d(4));arrow.lineTo(x+d(8),y+d(8));arrow.close();
            resetPaint();p.setColor(0xCC59B8FF);c.drawPath(arrow,p);c.restore();
            label(c,"GPX · EST.",x-d(22),y+d(25),8,0xCC59B8FF,false);
        }
        c.restore();
    }

    private void drawSensors(Canvas c,float l,float t,float width,float height) {
        box(c,l,t,l+width,t+height,14,0xEE071825,0x60567D96);
        label(c,"LIVE IMU / DEVICE AXES",l+d(13),t+d(22),10,CYAN,true);
        label(c,String.format(Locale.US,"ACCEL  %+.2f  %+.2f  %+.2f m/s²",imu[0],imu[1],imu[2]),l+d(13),t+d(44),10,INK,false);
        label(c,String.format(Locale.US,"GYRO   %+.2f  %+.2f  %+.2f rad/s",imu[3],imu[4],imu[5]),l+d(13),t+d(63),10,INK,false);
        label(c,String.format(Locale.US,"%.0f / %.0f Hz · acceleration includes gravity",imu[6],imu[7]),l+d(13),t+d(82),9,MUTED,false);
        float baseline=t+d(126);
        line(c,l+d(13),baseline,l+width-d(13),baseline,0x48668491,1);
        int stored=Math.min(historyIndex,90);
        for(int i=1;i<stored;i++) {
            int a=(historyIndex-stored+i-1)%90,b=(historyIndex-stored+i)%90;
            float ya=baseline-Math.max(-15,Math.min(15,accelerationHistory[a]-9.81f))*d(2);
            float yb=baseline-Math.max(-15,Math.min(15,accelerationHistory[b]-9.81f))*d(2);
            line(c,l+d(13)+(width-d(26))*(90-stored+i-1)/89,ya,l+d(13)+(width-d(26))*(90-stored+i)/89,yb,CYAN,1.3f);
        }
        label(c,scene==null?"|a| around 9.81 m/s² · diagnostic only":String.format(Locale.US,"YAW %+.0f°  PITCH %+.0f° · local axes, not north",Math.toDegrees(scene.heading),Math.toDegrees(scene.pitch)),l+d(13),t+height-d(10),9,MUTED,false);
    }

    private void drawReticle(Canvas c, int width, int height) {
        float cx=width*.5f,cy=height*.41f;
        disc(c,cx,cy,d(2),0xBFFFFFFF,false);
        line(c,cx-d(9),cy,cx-d(4),cy,0x90FFFFFF,1);
        line(c,cx+d(4),cy,cx+d(9),cy,0x90FFFFFF,1);

    }

    /** Camera bearing in local X/Z, not geographic north or a calibrated camera FOV. */
    private void drawPositionMarker(Canvas c,float x,float y,boolean oriented) {
        int tint=oriented?0xFF4DA8FF:0xFF96AAB8;
        if(oriented&&Float.isFinite(scene.heading)) {
            float heading=scene.heading,reach=d(18),spread=.42f;
            Path cone=new Path();cone.moveTo(x,y);
            cone.lineTo(x+(float)Math.sin(heading-spread)*reach,y+(float)Math.cos(heading-spread)*reach);
            cone.lineTo(x+(float)Math.sin(heading+spread)*reach,y+(float)Math.cos(heading+spread)*reach);cone.close();
            resetPaint();p.setColor(0x604DA8FF);c.drawPath(cone,p);
            line(c,x,y,x+(float)Math.sin(heading)*d(15),y+(float)Math.cos(heading)*d(15),0xCCBADDFF,1.5f);
        }
        disc(c,x,y,d(7),0xE6071825,false);
        disc(c,x,y,d(5),Color.WHITE,false);
        disc(c,x,y,d(3.6f),tint,false);
    }

    private void drawMiniMap(Canvas c,float l,float top,float width,float height,Odometer.Snapshot s) {
        box(c,l,top,l+width,top+height,10,0x90071825,0);
        label(c,"ROUTE · X/Z",l+d(10),top+d(20),9,MUTED,true);
        float centerX=l+width/2f,centerY=top+height*.56f;
        for(int i=-2;i<=2;i++){
            line(c,centerX+i*width/6f,top+d(31),centerX+i*width/6f,top+height-d(9),0x273B7886,.7f);
            line(c,l+d(10),centerY+i*height/7f,l+width-d(10),centerY+i*height/7f,0x273B7886,.7f);
        }
        if (s==null || s.worldTrail.isEmpty()) {
            disc(c,centerX,centerY,d(4),CYAN,false);
            label(c,"awaiting path",l+d(13),top+height-d(11),9,MUTED,false);
            return;
        }
        List<Odometer.Point> pts=s.worldTrail;
        boolean current=scene!=null&&scene.cameraPoseValid;
        double[] bounds=RouteHistory.bounds(pts);double midX=bounds[0],midZ=bounds[1],side=bounds[2];
        if(current)side=Math.max(side,2.5*Math.max(Math.abs(scene.cameraX-midX),Math.abs(scene.cameraZ-midZ)));
        double scale=(Math.min(width-d(28),height-d(49)))/side;
        Path path=new Path();
        for(int i=0;i<pts.size();i++) {
            Odometer.Point v=pts.get(i);
            float x=(float)(centerX+(v.x-midX)*scale);
            float y=(float)(centerY+(v.z-midZ)*scale);
            if(i==0 || pts.get(i-1).segment!=v.segment) path.moveTo(x,y);else path.lineTo(x,y);
        }
        resetPaint();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(2));p.setColor(CYAN);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        Odometer.Point last=pts.get(pts.size()-1);
        float endX=(float)(centerX+((current?scene.cameraX:last.x)-midX)*scale);
        float endY=(float)(centerY+((current?scene.cameraZ:last.z)-midZ)*scale);
        Odometer.Point first=pts.get(0);disc(c,(float)(centerX+(first.x-midX)*scale),(float)(centerY+(first.z-midZ)*scale),d(4),AMBER,true);
        c.save();c.clipRect(l+d(4),top+d(25),l+width-d(4),top+height-d(19));
        drawPositionMarker(c,endX,endY,current);c.restore();
        label(c,String.format(Locale.US,"%.0f m · route",side),l+d(10),top+height-d(8),9,MUTED,false);
    }

    /** Dense but bounded X/Z view of the current ARCore tracking segment. */
    private void drawLiveMap(Canvas c,float l,float top,float width,float height,Odometer.Snapshot s) {
        box(c,l,top,l+width,top+height,14,0xE9071825,0x8063A9B5);
        label(c,"FULL ROUTE + LIVE GEOMETRY · X / Z",l+d(11),top+d(19),9,CYAN,true);
        if(scene==null){label(c,"Waiting for tracked geometry…",l+d(15),top+d(48),11,MUTED,false);return;}
        double minX=scene.cameraX,maxX=minX,minZ=scene.cameraZ,maxZ=minZ;
        for(SceneMapper.MapPoint point:scene.mapPoints){minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);}
        for(float[] path:scene.path){minX=Math.min(minX,path[0]);maxX=Math.max(maxX,path[0]);minZ=Math.min(minZ,path[2]);maxZ=Math.max(maxZ,path[2]);}
        for(MapArchive.Poi poi:scene.pois){minX=Math.min(minX,poi.pose[0]);maxX=Math.max(maxX,poi.pose[0]);minZ=Math.min(minZ,poi.pose[2]);maxZ=Math.max(maxZ,poi.pose[2]);}
        if(s!=null)for(Odometer.Point point:s.worldTrail){minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);}
        double side=Math.max(1.5,Math.max(maxX-minX,maxZ-minZ)*1.18),midX=(maxX+minX)/2,midZ=(maxZ+minZ)/2;
        float mapTop=top+d(30),mapBottom=top+height-d(15),mapLeft=l+d(15),mapRight=l+width-d(15);
        float scale=(float)(Math.min(mapRight-mapLeft,mapBottom-mapTop)/side);
        float cx=(mapLeft+mapRight)/2,cy=(mapTop+mapBottom)/2;
        c.save();c.clipRect(mapLeft,mapTop,mapRight,mapBottom);
        for(int i=-4;i<=4;i++){
            float offset=i*(float)(side/8)*scale;
            line(c,cx+offset,mapTop,cx+offset,mapBottom,0x293B8793,.65f);
            line(c,mapLeft,cy+offset,mapRight,cy+offset,0x293B8793,.65f);
        }
        for(SceneMapper.MapSurface surface:scene.surfaces) {
            if(surface.xz.length<6)continue;Path boundary=new Path();
            for(int i=0;i<surface.xz.length;i+=2){float x=(float)(cx+(surface.xz[i]-midX)*scale),y=(float)(cy+(surface.xz[i+1]-midZ)*scale);if(i==0)boundary.moveTo(x,y);else boundary.lineTo(x,y);}
            boundary.close();resetPaint();p.setColor(surface.vertical?0xAAB5A0FF:0xAA74FCE1);p.setStrokeWidth(d(1.5f));p.setStyle(Paint.Style.STROKE);c.drawPath(boundary,p);p.setStyle(Paint.Style.FILL);
        }
        for(SceneMapper.MapPoint point:scene.mapPoints){
            float x=(float)(cx+(point.x-midX)*scale),y=(float)(cy+(point.z-midZ)*scale);
            int tint=point.y>1.5f?0xFFAB95FF:0xFFFFD080;disc(c,x,y,d(2f),tint,false);
        }
        Odometer.Point prior=null;
        if(s!=null)for(Odometer.Point pose:s.worldTrail){
            if(prior!=null&&prior.segment==pose.segment)line(c,(float)(cx+(prior.x-midX)*scale),(float)(cy+(prior.z-midZ)*scale),(float)(cx+(pose.x-midX)*scale),(float)(cy+(pose.z-midZ)*scale),CYAN,2.3f);prior=pose;
        }
        for(MapArchive.Poi poi:scene.pois){float x=(float)(cx+(poi.pose[0]-midX)*scale),y=(float)(cy+(poi.pose[2]-midZ)*scale);disc(c,x,y,d(5),AMBER,false);}
        float px=(float)(cx+(scene.cameraX-midX)*scale),py=(float)(cy+(scene.cameraZ-midZ)*scale);
        if(scene.cameraPoseValid)drawPositionMarker(c,px,py,true);
        else if(s!=null&&!s.worldTrail.isEmpty()){
            Odometer.Point last=s.worldTrail.get(s.worldTrail.size()-1);
            drawPositionMarker(c,(float)(cx+(last.x-midX)*scale),(float)(cy+(last.z-midZ)*scale),false);
        }
        c.restore();
        label(c,String.format(Locale.US,"%d scene points · %d planes · %d markers · %.1f m view",scene.mapPoints.size(),scene.horizontal+scene.vertical,scene.pois.size(),side),l+d(11),top+height-d(4),9,MUTED,false);
    }

    private void drawDataPanel(Canvas c,int w,int h,Odometer.Snapshot s) {
        float left=d(12),right=w-d(12),top=h-d(158),bottom=h-d(64);
        box(c,left,top,right,bottom,12,0xB0071925,0);
        label(c,s!=null&&s.recording?"● REC":"Ⅱ PAUSED",left+d(10),top+d(17),9,s!=null&&s.recording?CYAN:AMBER,true);
        if(s==null)return;
        float second=left+(right-left)*.53f;
        label(c,String.format(Locale.US,"%.2f m",s.totalMeters),left+d(10),top+d(48),25,INK,true);
        label(c,String.format(Locale.US,"%.1f km/h",s.speedMps*3.6),second,top+d(48),22,INK,true);
        label(c,"VIO DISTANCE",left+d(11),top+d(64),8,MUTED,false);
        label(c,"VIO SPEED",second,top+d(64),8,MUTED,false);
        if(!guideText.isEmpty()){
            box(c,left,top-d(28),right,top-d(3),8,0xD0071925,0);
            label(c,guideText,left+d(9),top-d(12),9,AMBER,true);
        }
        long seconds=s.elapsedMilliseconds/1000;
        label(c,String.format(Locale.US,"%02d:%02d · Δh %+.2f m · gaps %d",seconds/60,seconds%60,s.verticalMeters,s.gaps),left+d(10),top+d(83),9,MUTED,false);

    }
}
