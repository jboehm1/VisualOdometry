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
    private int keyframes=0,photos=0,poiCount=0;
    private boolean mapFull=false;
    public void updateCapture(int frames,int images,boolean full){mapFull=full;updateCapture(frames,images,poiCount);}
    public void updateCapture(int frames,int images,int markers,boolean full){mapFull=full;updateCapture(frames,images,markers);}
    public void updateCapture(int keyframes,int photos){updateCapture(keyframes,photos,poiCount);}
    public void updateCapture(int frames,int images,int markers){this.keyframes=frames;this.photos=images;poiCount=markers;invalidate();}
    private SceneMapper.Snapshot scene;
    private float[] imu=new float[8];
    private float cameraHz;
    private int layers=0;
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
        resetPaint(); p.setStrokeWidth(d(width)); p.setColor(color);
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

        resetPaint();
        p.setShader(new LinearGradient(0,0,0,d(175),
                0xEA07121D,0x0007121D, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,d(175),p);
        p.setShader(new LinearGradient(0,h-d(330),0,h,
                0x0007121D,0xE9000E18,Shader.TileMode.CLAMP));
        c.drawRect(0,h-d(330),w,h,p);
        drawScene(c,w,h);
        drawReticle(c,w,h);

        label(c,"VECTOR",d(20),d(52),d(25)/dp,INK,true);
        label(c,"/ SPATIAL EXPLORER  ·  0.5",d(20),d(72),10,MUTED,false);
        box(c,w-d(132),d(32),w-d(16),d(63),10,0xCF0B2630,0x5539F4D2);
        disc(c,w-d(118),d(47),d(4),s!=null&&s.tracking?CYAN:AMBER,false);
        String tiny=trackingText.length()>14?trackingText.substring(0,14):trackingText;
        label(c,tiny,w-d(107),d(51),10,INK,true);

        label(c,"6-DOF   •   METRIC ESTIMATE   •   NO GPS",d(20),d(105),10,CYAN,true);
        label(c,notice.length()>47?notice.substring(0,47)+"…":notice,d(20),d(125),10,MUTED,false);

        label(c,new String[]{"PLANES + POINTS + TRAIL","PLANES + TRAIL","POINTS + TRAIL","TRAIL ONLY"}[layers],d(20),d(208),9,CYAN,true);
        if(scene!=null) {
            label(c,String.format(Locale.US,"%d H / %d V PLANES   ·   %d POINTS   ·   %.0f FPS",scene.horizontal,scene.vertical,scene.points,cameraHz),d(20),d(226),9,INK,true);
        }
        if(scene!=null) label(c,String.format(Locale.US,"MAP %d KF%s · %d IMG · %d POI · GAPS %d · REJECT %d",keyframes,mapFull?" FULL":"",photos,poiCount,s==null?0:s.gaps,s==null?0:s.outliers),d(20),d(242),9,MUTED,false);
        if(expandedMap) drawLiveMap(c,d(14),d(253),w-d(28),Math.max(d(80),h-d(564)),s);
        else if(sensors) drawSensors(c,d(14),h-d(478),w-d(28),d(168));
        else drawMiniMap(c,w-d(157),h-d(454),d(137),d(145),s);
        drawDataPanel(c,w,h,s);
    }

    private void drawScene(Canvas c,int w,int h) {
        if(scene==null)return;
        c.save();c.clipRect(0,d(247),w,h-d(305));
        for(SceneMapper.Shape shape:scene.shapes) {
            if(shape.kind<2 && (layers==2 || layers==3))continue;
            if(shape.kind==2 && (layers==1 || layers==3))continue;
            float[] xy=shape.xy;
            int color=shape.kind==1?0xFFB5A0FF:CYAN;
            if(shape.kind==2){disc(c,xy[0]*w,xy[1]*h,d(1.8f),0xB0F8D58A,false);continue;}
            if(shape.kind==3){
                line(c,xy[0]*w,xy[1]*h,xy[2]*w,xy[3]*h,0x4044E8FD,9);
                line(c,xy[0]*w,xy[1]*h,xy[2]*w,xy[3]*h,0xFF7AF2FF,2.5f);continue;
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
            path.close();resetPaint();p.setColor((color&0xFFFFFF)|0x20000000);c.drawPath(path,p);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1.2f));c.drawPath(path,p);
            cx/=xy.length/2f;cy/=xy.length/2f;
            cx=Math.max(d(88),Math.min(w-d(88),cx));
            cy=Math.max(d(266),Math.min(h-d(322),cy));
            box(c,cx-d(84),cy-d(16),cx+d(84),cy+d(7),6,0xD5091C2B,0);
            label(c,shape.label,cx-d(78),cy,9,color,true);
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
        disc(c,cx,cy,d(38),0x6674FCE1,true);
        disc(c,cx,cy,d(60),0x2874FCE1,true);
        disc(c,cx,cy,d(2.5f),CYAN,false);
        if(scene!=null && scene.targetMeters>=0) {
            box(c,cx-d(58),cy+d(74),cx+d(58),cy+d(99),8,0xDD071925,0);
            label(c,String.format(Locale.US,"SURFACE %.2f m",scene.targetMeters),cx-d(51),cy+d(91),10,CYAN,true);
        }
        line(c,cx-d(74),cy,cx-d(48),cy,0xAA74FCE1,1.1f);
        line(c,cx+d(48),cy,cx+d(74),cy,0xAA74FCE1,1.1f);
        line(c,cx,cy-d(74),cx,cy-d(48),0xAA74FCE1,1.1f);
        line(c,cx,cy+d(48),cx,cy+d(74),0xAA74FCE1,1.1f);
        line(c,d(20),cy, d(41),cy,0x8874FCE1,1);
        line(c,width-d(41),cy,width-d(20),cy,0x8874FCE1,1);

    }

    private void drawMiniMap(Canvas c,float l,float top,float width,float height,Odometer.Snapshot s) {
        box(c,l,top,l+width,top+height,13,0xDB071825,0x6048A6AD);
        label(c,"SEGMENT MAP · X / Z",l+d(10),top+d(20),9,MUTED,true);
        float centerX=l+width/2f,centerY=top+height*.56f;
        for(int i=-2;i<=2;i++){
            line(c,centerX+i*width/6f,top+d(31),centerX+i*width/6f,top+height-d(9),0x273B7886,.7f);
            line(c,l+d(10),centerY+i*height/7f,l+width-d(10),centerY+i*height/7f,0x273B7886,.7f);
        }
        if (s==null || s.trail.isEmpty()) {
            disc(c,centerX,centerY,d(4),CYAN,false);
            label(c,"awaiting path",l+d(13),top+height-d(11),9,MUTED,false);
            return;
        }
        List<Odometer.Point> pts=s.trail;
        double loX=0,hiX=0,loZ=0,hiZ=0;
        for(Odometer.Point v:pts) {
            if(v.segment!=s.segments)continue;
            loX=Math.min(loX,v.x);hiX=Math.max(hiX,v.x);
            loZ=Math.min(loZ,v.z);hiZ=Math.max(hiZ,v.z);
        }
        double side=Math.max(2,Math.max(hiX-loX,hiZ-loZ)*1.25);
        double midX=(hiX+loX)/2,midZ=(hiZ+loZ)/2;
        double scale=(Math.min(width-d(28),height-d(49)))/side;
        Path path=new Path();
        for(int i=0;i<pts.size();i++) {
            Odometer.Point v=pts.get(i);
            float x=(float)(centerX+(v.x-midX)*scale);
            float y=(float)(centerY+(v.z-midZ)*scale);
            if(v.segment!=s.segments)continue;
            if(i==0 || pts.get(i-1).segment!=v.segment) path.moveTo(x,y);else path.lineTo(x,y);
        }
        resetPaint();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(2));p.setColor(CYAN);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        Odometer.Point last=pts.get(pts.size()-1);
        float endX=(float)(centerX+(last.x-midX)*scale);
        float endY=(float)(centerY+(last.z-midZ)*scale);
        disc(c,(float)(centerX-midX*scale),(float)(centerY-midZ*scale),d(4),AMBER,true);
        disc(c,endX,endY,d(4.5f),CYAN,false);
        if(scene!=null)line(c,endX,endY,endX+(float)Math.sin(scene.heading)*d(14),endY+(float)Math.cos(scene.heading)*d(14),CYAN,2);
        label(c,String.format(Locale.US,"%.0f m window",side),l+d(10),top+height-d(8),9,MUTED,false);
    }

    /** Dense but bounded X/Z view of the current ARCore tracking segment. */
    private void drawLiveMap(Canvas c,float l,float top,float width,float height,Odometer.Snapshot s) {
        box(c,l,top,l+width,top+height,14,0xE9071825,0x8063A9B5);
        label(c,"LIVE SCENE MAP · X / Z · CURRENT SEGMENT",l+d(11),top+d(19),9,CYAN,true);
        if(scene==null){label(c,"Waiting for tracked geometry…",l+d(15),top+d(48),11,MUTED,false);return;}
        double minX=scene.cameraX,maxX=minX,minZ=scene.cameraZ,maxZ=minZ;
        for(SceneMapper.MapPoint point:scene.mapPoints){minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);}
        for(float[] path:scene.path){minX=Math.min(minX,path[0]);maxX=Math.max(maxX,path[0]);minZ=Math.min(minZ,path[2]);maxZ=Math.max(maxZ,path[2]);}
        for(MapArchive.Poi poi:scene.pois){minX=Math.min(minX,poi.pose[0]);maxX=Math.max(maxX,poi.pose[0]);minZ=Math.min(minZ,poi.pose[2]);maxZ=Math.max(maxZ,poi.pose[2]);}
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
        float[] prior=null;
        for(float[] pose:scene.path){float x=(float)(cx+(pose[0]-midX)*scale),y=(float)(cy+(pose[2]-midZ)*scale);
            if(prior!=null)line(c,prior[0],prior[1],x,y,0xFF74FCE1,2.3f);prior=new float[]{x,y};}
        for(MapArchive.Poi poi:scene.pois){float x=(float)(cx+(poi.pose[0]-midX)*scale),y=(float)(cy+(poi.pose[2]-midZ)*scale);disc(c,x,y,d(5),AMBER,false);}
        float px=(float)(cx+(scene.cameraX-midX)*scale),py=(float)(cy+(scene.cameraZ-midZ)*scale);
        disc(c,px,py,d(4),Color.WHITE,false);
        line(c,px,py,px+(float)Math.sin(scene.heading)*d(15),py+(float)Math.cos(scene.heading)*d(15),Color.WHITE,2);
        c.restore();
        label(c,String.format(Locale.US,"%d scene points · %d planes · %d markers · %.1f m view",scene.mapPoints.size(),scene.horizontal+scene.vertical,scene.pois.size(),side),l+d(11),top+height-d(4),9,MUTED,false);
    }

    private void drawDataPanel(Canvas c,int w,int h,Odometer.Snapshot s) {
        float left=d(14),right=w-d(14),top=h-d(299),bottom=h-d(103);
        box(c,left,top,right,bottom,19,0xED071925,0x65568188);
        line(c,left+d(16),top+d(45),right-d(16),top+d(45),0x344C7386,1);
        label(c,"LIVE TELEMETRY",left+d(17),top+d(27),11,CYAN,true);
        label(c,s!=null&&s.recording?"●  REC":"Ⅱ  IDLE",right-d(80),top+d(27),11,
              s!=null&&s.recording?CYAN:AMBER,true);

        if(s==null) {
            label(c,"Waiting for ARCore…",left+d(18),top+d(100),16,INK,false);
            return;
        }
        float y1=top+d(78);
        label(c,"PATH DISTANCE",left+d(18),y1,9,MUTED,true);
        label(c,String.format(Locale.US,"%.2f",s.totalMeters),left+d(17),y1+d(40),33,INK,true);
        label(c,"m",left+d(127),y1+d(36),13,CYAN,true);

        float secondColumn=left+(right-left)*.55f;
        label(c,"SPEED",secondColumn,y1,9,MUTED,true);
        label(c,String.format(Locale.US,"%.1f",s.speedMps*3.6),secondColumn,y1+d(40),33,INK,true);
        label(c,"km/h",secondColumn+d(89),y1+d(36),11,CYAN,true);

        line(c,left+d(17),top+d(132),right-d(17),top+d(132),0x344C7386,1);
        label(c,"SEGMENT OFFSET",left+d(18),top+d(153),9,MUTED,true);
        label(c,String.format(Locale.US,"%.2f m",s.displacementMeters),left+d(18),top+d(176),17,INK,true);
        label(c,"Δ HEIGHT",secondColumn,top+d(153),9,MUTED,true);
        label(c,String.format(Locale.US,"%+.2f m",s.verticalMeters),secondColumn,top+d(176),17,INK,true);

        float seconds=s.elapsedMilliseconds/1000f;
        label(c,String.format(Locale.US,"TIME %02d:%02d  •  SEGMENTS %d  •  MARKERS %d",
                    (int)(seconds/60),(int)(seconds%60),s.segments,markers),
                    left+d(18),bottom-d(9),9,MUTED,false);
    }
}
