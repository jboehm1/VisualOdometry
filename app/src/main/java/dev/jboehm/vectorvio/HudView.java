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
        drawReticle(c,w,h);

        label(c,"VECTOR",d(20),d(52),d(25)/dp,INK,true);
        label(c,"/ VISUAL–INERTIAL ODOMETER",d(20),d(72),10,MUTED,false);
        box(c,w-d(132),d(32),w-d(16),d(63),10,0xCF0B2630,0x5539F4D2);
        disc(c,w-d(118),d(47),d(4),s!=null&&s.tracking?CYAN:AMBER,false);
        String tiny=trackingText.length()>14?trackingText.substring(0,14):trackingText;
        label(c,tiny,w-d(107),d(51),10,INK,true);

        label(c,"6-DOF   •   METRIC ESTIMATE   •   NO GPS",d(20),d(105),10,CYAN,true);
        label(c,notice.length()>47?notice.substring(0,47)+"…":notice,d(20),d(125),10,MUTED,false);

        drawMiniMap(c,w-d(157),h-d(444),d(137),d(135),s);
        drawDataPanel(c,w,h,s);
    }

    private void drawReticle(Canvas c, int width, int height) {
        float cx=width*.5f,cy=height*.41f;
        disc(c,cx,cy,d(38),0x6674FCE1,true);
        disc(c,cx,cy,d(60),0x2874FCE1,true);
        disc(c,cx,cy,d(2.5f),CYAN,false);
        line(c,cx-d(74),cy,cx-d(48),cy,0xAA74FCE1,1.1f);
        line(c,cx+d(48),cy,cx+d(74),cy,0xAA74FCE1,1.1f);
        line(c,cx,cy-d(74),cx,cy-d(48),0xAA74FCE1,1.1f);
        line(c,cx,cy+d(48),cx,cy+d(74),0xAA74FCE1,1.1f);
        line(c,d(20),cy, d(41),cy,0x8874FCE1,1);
        line(c,width-d(41),cy,width-d(20),cy,0x8874FCE1,1);
        label(c,"TRACKING VIEW  /  CAMERA FRAME",d(20),cy+d(91),9,0xBFD5E6EE,false);
    }

    private void drawMiniMap(Canvas c,float l,float top,float width,float height,Odometer.Snapshot s) {
        box(c,l,top,l+width,top+height,13,0xDB071825,0x6048A6AD);
        label(c,"TOP VIEW · X / Z",l+d(10),top+d(20),9,MUTED,true);
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
            if(i==0) path.moveTo(x,y);else path.lineTo(x,y);
        }
        resetPaint();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(2));p.setColor(CYAN);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        Odometer.Point last=pts.get(pts.size()-1);
        float endX=(float)(centerX+(last.x-midX)*scale);
        float endY=(float)(centerY+(last.z-midZ)*scale);
        disc(c,endX,endY,d(4.5f),CYAN,false);
        label(c,String.format(Locale.US,"%.0f m window",side),l+d(10),top+height-d(8),9,MUTED,false);
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
        label(c,"DISPLACEMENT",left+d(18),top+d(153),9,MUTED,true);
        label(c,String.format(Locale.US,"%.2f m",s.displacementMeters),left+d(18),top+d(176),17,INK,true);
        label(c,"Δ HEIGHT",secondColumn,top+d(153),9,MUTED,true);
        label(c,String.format(Locale.US,"%+.2f m",s.verticalMeters),secondColumn,top+d(176),17,INK,true);

        float seconds=s.elapsedMilliseconds/1000f;
        label(c,String.format(Locale.US,"TIME %02d:%02d  •  SEGMENTS %d  •  MARKERS %d",
                    (int)(seconds/60),(int)(seconds%60),s.segments,markers),
                    left+d(18),bottom-d(9),9,MUTED,false);
    }
}
