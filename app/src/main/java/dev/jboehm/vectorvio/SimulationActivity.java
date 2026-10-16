package dev.jboehm.vectorvio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Offline generated camera-video and synchronized deterministic IMU replay. */
public final class SimulationActivity extends Activity {
    private static final int EXPORT_REQUEST=501;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final long[] speeds={500_000_000L,1_000_000_000L,1_500_000_000L};
    private int speedIndex=1;
    private boolean playing=true,loop=true;
    private long baseSimNs=0,baseClockNs;
    private SimulationView simulationView;
    private TextView playButton,speedButton,loopButton;
    private final Runnable render=new Runnable(){@Override public void run(){
        if(!playing&&!loop&&baseSimNs>=SimulationModel.DURATION_NS)baseSimNs=SimulationModel.DURATION_NS-1;
        long time=currentTime();
        if(!loop&&time>=SimulationModel.DURATION_NS){time=SimulationModel.DURATION_NS-1;baseSimNs=time;playing=false;}
        simulationView.setReplay(SimulationModel.sample(time),time,playing,speeds[speedIndex]/1e9);
        if(playButton!=null)playButton.setText(playing?"PAUSE":"PLAY");
        handler.postDelayed(this,33);
    }};

    private long currentTime(){
        long time=baseSimNs+(playing?(long)((SystemClock.elapsedRealtimeNanos()-baseClockNs)*(speeds[speedIndex]/1e9)):0);
        return loop?time%SimulationModel.DURATION_NS:Math.min(time,SimulationModel.DURATION_NS);
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        baseClockNs=SystemClock.elapsedRealtimeNanos();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF07121D);
        simulationView=new SimulationView();root.addView(simulationView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setPadding(dp(6),dp(7),dp(6),dp(7));
        controls.setBackgroundColor(0xE9081924);
        playButton=button("PAUSE");speedButton=button("1.0×");TextView reset=button("RESET");loopButton=button("LOOP ON");TextView export=button("CSV");
        controls.addView(playButton,weighted());controls.addView(speedButton,weighted());controls.addView(reset,weighted());controls.addView(loopButton,weighted());controls.addView(export,weighted());
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM);root.addView(controls,cp);
        playButton.setOnClickListener(v->{if(playing){baseSimNs=currentTime();playing=false;}else{baseClockNs=SystemClock.elapsedRealtimeNanos();playing=true;}tickNow();});
        speedButton.setOnClickListener(v->{baseSimNs=currentTime();baseClockNs=SystemClock.elapsedRealtimeNanos();speedIndex=(speedIndex+1)%speeds.length;speedButton.setText(String.format(Locale.US,"%.1f×",speeds[speedIndex]/1e9));tickNow();});
        reset.setOnClickListener(v->{baseSimNs=0;baseClockNs=SystemClock.elapsedRealtimeNanos();playing=true;tickNow();});
        loopButton.setOnClickListener(v->{loop=!loop;loopButton.setText(loop?"LOOP ON":"LOOP OFF");if(loop)baseClockNs=SystemClock.elapsedRealtimeNanos();});
        export.setOnClickListener(v->exportCsv());
        simulationView.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("About this simulation")
            .setMessage("A deterministic 40-second indoor walk is rendered locally at playback time. Ground-truth pose and synthetic, time-synchronized accelerometer and gyroscope values come from the same trajectory. A fixed seed and repeatable reset make runs comparable.\n\nThis generated scene is not camera footage and the simulated IMU is not a measurement from a real phone. Use it to test UI, playback, logging, and downstream math without light, camera permission, internet, or ARCore tracking.")
            .setPositiveButton("OK",null).show());
        setContentView(root);tickNow();
    }

    private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(2),0,dp(2),0);return p;}
    private TextView button(String title){TextView v=new TextView(this);v.setText(title);v.setTextColor(Color.WHITE);v.setTextSize(10);v.setGravity(Gravity.CENTER);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));v.setBackgroundColor(0xFF173443);v.setContentDescription(title);return v;}
    private void tickNow(){handler.removeCallbacks(render);handler.post(render);}

    private void exportCsv(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
            .putExtra(Intent.EXTRA_TITLE,"vector_simulation_seed_"+Long.toHexString(SimulationModel.SEED)+".csv");
        try{startActivityForResult(intent,EXPORT_REQUEST);}catch(Exception e){Toast.makeText(this,"No document provider available",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();new Thread(()->{
            String status="Simulation CSV saved";
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new java.io.IOException("Cannot open destination");
                StringBuilder csv=new StringBuilder(500_000);
                csv.append("# VECTOR deterministic synthetic walk v1, seed=").append(Long.toHexString(SimulationModel.SEED)).append(", sample_rate_hz=").append(SimulationModel.SAMPLE_RATE_HZ).append(", duration_s=40\n");
                csv.append("time_ns,x_m,y_m,z_m,yaw_rad,pitch_rad,roll_rad,accel_x_m_s2,accel_y_m_s2,accel_z_m_s2,gyro_x_rad_s,gyro_y_rad_s,gyro_z_rad_s\n");
                for(int i=0;i<SimulationModel.SAMPLE_RATE_HZ*40;i++){
                    SimulationModel.Sample s=SimulationModel.sample(i*1_000_000_000L/SimulationModel.SAMPLE_RATE_HZ);
                    csv.append(String.format(Locale.US,"%d,%.6f,%.6f,%.6f,%.7f,%.7f,%.7f,%.6f,%.6f,%.6f,%.7f,%.7f,%.7f\n",
                        s.timeNs,s.x,s.y,s.z,s.yaw,s.pitch,s.roll,s.ax,s.ay,s.az,s.gx,s.gy,s.gz));
                }
                out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Exception e){status="Export failed: "+e.getMessage();}
            String resultText=status;runOnUiThread(()->Toast.makeText(this,resultText,Toast.LENGTH_LONG).show());
        }).start();
    }
    @Override protected void onResume(){super.onResume();if(playing)baseClockNs=SystemClock.elapsedRealtimeNanos();tickNow();}
    @Override protected void onPause(){handler.removeCallbacks(render);if(playing)baseSimNs=currentTime();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(render);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();}

    private final class SimulationView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box=new RectF();
        private SimulationModel.Sample sample=SimulationModel.sample(0);
        private long timeNs=0;private boolean active=true;private double speed=1;
        private final float density=getResources().getDisplayMetrics().density;
        SimulationView(){super(SimulationActivity.this);setClickable(true);}
        void setReplay(SimulationModel.Sample s,long t,boolean play,double rate){sample=s;timeNs=t;active=play;speed=rate;invalidate();}
        private float d(float n){return n*density;}
        private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setTextSize(d(size));paint.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,y,paint);}
        private void panel(Canvas c,float l,float t,float r,float b){paint.reset();paint.setAntiAlias(true);paint.setColor(0xE90A1B27);c.drawRoundRect(l,t,r,b,d(14),d(14),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(0x6656B8BE);c.drawRoundRect(l,t,r,b,d(14),d(14),paint);paint.setStyle(Paint.Style.FILL);}
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setStrokeWidth(d(width));paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,paint);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            c.drawColor(0xFF101E27);drawVideo(c,w,h);drawTop(c,w);drawMap(c,w,h);drawTelemetry(c,w,h);
        }
        private void drawVideo(Canvas c,int w,int h){
            float horizon=h*.39f-(float)(sample.pitch*h*.65);paint.setShader(new android.graphics.LinearGradient(0,0,0,horizon,0xFF334959,0xFF9AA6A3,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,horizon,paint);paint.setShader(null);
            quad(c,-5,0,-6,5,0,-6,5,0,6,-5,0,6,0xFF697976,0xFF8FA19B);
            quad(c,-5,0,-6,-5,0,6,-5,2.85,6,-5,2.85,-6,0xFF394F59,0xFF5C6C6D);
            quad(c,5,0,-6,5,0,6,5,2.85,6,5,2.85,-6,0xFF3E525B,0xFF596A6C);
            quad(c,-5,0,-6,5,0,-6,5,2.85,-6,-5,2.85,-6,0xFF4A5D63,0xFF63777B);
            for(int i=-5;i<=5;i++)worldLine(c,i*.8,0,-6,i*.8,0,6,0x6679A49B,.7f);
            for(int i=-6;i<=6;i++)worldLine(c,-5,0,i,5,0,i,0x6679A49B,.7f);
            wallArt(c,-1.7,-5.96,1.1,.8,0xFF2F5964);wallArt(c,2.1,-5.96,.72,1.35,0xFFDAA76E);
            box3d(c,-2.1,.68,-.4,.9,.68,.75,0xFF745D4E);box3d(c,.8,.42,1.15,1.35,.42,.7,0xFF536D64);
            box3d(c,-3.7,.9,2.1,.42,1.8,.42,0xFFB08C61);box3d(c,3.15,.65,-2.3,.55,1.3,.65,0xFF64727D);
            light(c,-1.7,2.83,-1.6);light(c,1.8,2.83,-3.9);light(c,3.3,2.83,1.5);
            drawReticle(c,w,h);
            panel(c,d(14),horizon+d(20),d(203),horizon+d(51));text(c,"SYNTHETIC CAMERA VIDEO",d(25),horizon+d(40),9,0xFFE9F9FF,true);
        }
        private void drawTop(Canvas c,int w){
            paint.reset();paint.setShader(new android.graphics.LinearGradient(0,0,0,d(147),0xF0081722,0x00081722,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,d(147),paint);paint.setShader(null);
            text(c,"VECTOR  /  SIMULATION",d(18),d(40),17,0xFFF1FAFF,true);
            text(c,"OFFLINE · DETERMINISTIC · NO CAMERA INPUT",d(18),d(61),9,0xFF74FCE1,true);
            float progress=(float)(timeNs/(double)SimulationModel.DURATION_NS);String state=active?"PLAYING":"PAUSED";
            text(c,state+"  ·  "+String.format(Locale.US,"%.1f×   ·   %02d:%02d / 00:40",speed,(int)(timeNs/1e9)/60,(int)(timeNs/1e9)%60),d(18),d(84),10,0xFFF1FAFF,true);
            box.set(d(18),d(96),getWidth()-d(18),d(99));paint.reset();paint.setColor(0x553D7280);c.drawRoundRect(box,d(3),d(3),paint);box.right=box.left+(getWidth()-d(36))*progress;paint.setColor(0xFF74FCE1);c.drawRoundRect(box,d(3),d(3),paint);
        }
        private void drawMap(Canvas c,int w,int h){
            float l=w-d(155),t=d(115),r=w-d(14),b=t+d(147);panel(c,l,t,r,b);text(c,"GROUND TRUTH",l+d(10),t+d(19),8,0xFFAAC0CC,true);
            float cx=(l+r)*.5f,cy=t+d(88),scale=d(15);Path path=new Path();
            for(int i=0;i<=120;i++){double a=2*Math.PI*i/120;float x=cx+(float)(2.65*Math.cos(a))*scale,z=cy+(float)(3.35*Math.sin(a))*scale;if(i==0)path.moveTo(x,z);else path.lineTo(x,z);}
            paint.reset();paint.setAntiAlias(true);paint.setColor(0xFF55D9D8);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1.5f));c.drawPath(path,paint);
            float px=cx+sample.x*scale,pz=cy+sample.z*scale;paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFFFCA75);c.drawCircle(px,pz,d(4),paint);
            float headingX=(float)Math.sin(sample.yaw),headingZ=(float)-Math.cos(sample.yaw);line(c,px,pz,px+headingX*d(10),pz+headingZ*d(10),0xFFFFCA75,1.4f);
            text(c,"X / Z · METRES",l+d(10),b-d(10),8,0xFFAAC0CC,false);
        }
        private void drawTelemetry(Canvas c,int w,int h){
            float top=h-d(212),left=d(14),right=w-d(14),bottom=h-d(72);panel(c,left,top,right,bottom);
            text(c,"SYNCHRONIZED SYNTHETIC IMU",left+d(13),top+d(23),10,0xFF74FCE1,true);
            text(c,String.format(Locale.US,"ACCEL   %+.2f   %+.2f   %+.2f  m/s²",sample.ax,sample.ay,sample.az),left+d(13),top+d(48),10,0xFFF1FAFF,false);
            text(c,String.format(Locale.US,"GYRO    %+.3f   %+.3f   %+.3f  rad/s",sample.gx,sample.gy,sample.gz),left+d(13),top+d(69),10,0xFFF1FAFF,false);
            text(c,String.format(Locale.US,"POSE    %+.2f  %+.2f  %+.2f m   ·   YAW %+.0f°",sample.x,sample.y,sample.z,Math.toDegrees(sample.yaw)),left+d(13),top+d(90),9,0xFFAAC0CC,false);
            float mid=top+d(116);line(c,left+d(13),mid,right-d(13),mid,0x554F7684,1);
            float t=(float)(timeNs*1e-9);for(int i=1;i<44;i++){float f0=(i-1)/43f,f1=i/43f;float x0=left+d(13)+(right-left-d(26))*f0,x1=left+d(13)+(right-left-d(26))*f1;float a0=(float)(Math.sin((t-(1-f0)*2)*2*Math.PI*2.05)+.22*Math.sin((t-(1-f0)*2)*2*Math.PI*8.3));float a1=(float)(Math.sin((t-(1-f1)*2)*2*Math.PI*2.05)+.22*Math.sin((t-(1-f1)*2)*2*Math.PI*8.3));line(c,x0,mid-a0*d(6),x1,mid-a1*d(6),0xFFFFC976,1.1f);}
            text(c,"60 Hz ground-truth stream  ·  deterministic seed "+Long.toHexString(SimulationModel.SEED),left+d(13),bottom-d(10),8,0xFFAAC0CC,false);
        }
        private float[] project(double x,double y,double z){
            double dx=x-sample.x,dy=y-sample.y,dz=z-sample.z,co=Math.cos(sample.yaw),si=Math.sin(sample.yaw);
            double right=co*dx-si*dz,forward=-si*dx-co*dz;if(forward<.12)return null;
            double up=dy*Math.cos(sample.pitch)-forward*Math.sin(sample.pitch);float focal=getWidth()*.88f;
            return new float[]{getWidth()*.5f+(float)(right*focal/forward),getHeight()*.40f-(float)(up*focal/forward),(float)forward};
        }
        private void quad(Canvas c,double x1,double y1,double z1,double x2,double y2,double z2,double x3,double y3,double z3,double x4,double y4,double z4,int fill,int stroke){
            float[] a=project(x1,y1,z1),b=project(x2,y2,z2),d=project(x3,y3,z3),e=project(x4,y4,z4);if(a==null||b==null||d==null||e==null)return;
            Path p=new Path();p.moveTo(a[0],a[1]);p.lineTo(b[0],b[1]);p.lineTo(d[0],d[1]);p.lineTo(e[0],e[1]);p.close();paint.reset();paint.setAntiAlias(true);paint.setColor(fill);c.drawPath(p,paint);paint.setColor(stroke);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(.8f));c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);
        }
        private void worldLine(Canvas c,double x1,double y1,double z1,double x2,double y2,double z2,int color,float width){float[] a=project(x1,y1,z1),b=project(x2,y2,z2);if(a!=null&&b!=null)line(c,a[0],a[1],b[0],b[1],color,width);}
        private void wallArt(Canvas c,double x,double z,double width,double height,int color){quad(c,x-width/2,.75,z,x+width/2,.75,z,x+width/2,.75+height,z,x-width/2,.75+height,z,color,0xFFB7C5BC);}
        private void light(Canvas c,double x,double y,double z){float[] q=project(x,y,z);if(q!=null){paint.reset();paint.setAntiAlias(true);paint.setColor(0xFFFFE8B3);c.drawCircle(q[0],q[1],d(3),paint);}}
        private void box3d(Canvas c,double x,double y,double z,double width,double height,double depth,int color){
            double x0=x-width/2,x1=x+width/2,z0=z-depth/2,z1=z+depth/2;
            quad(c,x0,y,z0,x1,y,z0,x1,y+height,z0,x0,y+height,z0,color,0x667B978A);
            quad(c,x1,y,z0,x1,y,z1,x1,y+height,z1,x1,y+height,z0,darken(color),0x667B978A);
            quad(c,x0,y+height,z0,x1,y+height,z0,x1,y+height,z1,x0,y+height,z1,lighten(color),0x667B978A);
        }
        private int darken(int color){return Color.rgb((Color.red(color)*3)/4,(Color.green(color)*3)/4,(Color.blue(color)*3)/4);}
        private int lighten(int color){return Color.rgb(Math.min(255,Color.red(color)+28),Math.min(255,Color.green(color)+28),Math.min(255,Color.blue(color)+28));}
        private void drawReticle(Canvas c,int w,int h){float x=w*.5f,y=h*.4f;paint.reset();paint.setAntiAlias(true);paint.setColor(0x9974FCE1);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));c.drawCircle(x,y,d(17),paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,d(2),paint);}
    }
}
