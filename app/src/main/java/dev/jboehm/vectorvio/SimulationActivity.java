package dev.jboehm.vectorvio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
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
    private TextView playButton,speedButton,loopButton,lightButton;
    private int lightIndex=0;
    private static final float[] EXPOSURES={1f,.38f,.14f,.045f};
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
        playButton=button("PAUSE");speedButton=button("1.0×");TextView reset=button("RESET");loopButton=button("LOOP ON");lightButton=button("LIGHT 100%");TextView export=button("CSV");
        controls.addView(playButton,weighted());controls.addView(speedButton,weighted());controls.addView(reset,weighted());controls.addView(loopButton,weighted());controls.addView(lightButton,weighted());controls.addView(export,weighted());
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM);root.addView(controls,cp);
        playButton.setOnClickListener(v->{if(playing){baseSimNs=currentTime();playing=false;}else{baseClockNs=SystemClock.elapsedRealtimeNanos();playing=true;}tickNow();});
        speedButton.setOnClickListener(v->{baseSimNs=currentTime();baseClockNs=SystemClock.elapsedRealtimeNanos();speedIndex=(speedIndex+1)%speeds.length;speedButton.setText(String.format(Locale.US,"%.1f×",speeds[speedIndex]/1e9));tickNow();});
        reset.setOnClickListener(v->{baseSimNs=0;baseClockNs=SystemClock.elapsedRealtimeNanos();playing=true;tickNow();});
        loopButton.setOnClickListener(v->{loop=!loop;loopButton.setText(loop?"LOOP ON":"LOOP OFF");if(loop)baseClockNs=SystemClock.elapsedRealtimeNanos();});
        lightButton.setTextSize(8);lightButton.setOnClickListener(v->{lightIndex=(lightIndex+1)%EXPOSURES.length;lightButton.setText(new String[]{"LIGHT 100%","LIGHT 38%","LIGHT 14%","DARK 4%"}[lightIndex]);simulationView.invalidate();});
        export.setOnClickListener(v->exportCsv());
        simulationView.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("About this simulation")
            .setMessage("A deterministic 40-second square-loop corridor walk is rendered locally at playback time. A separate image-only feature tracker processes the rendered frames and builds the displayed relative path and feature landmarks; it receives no ground-truth pose or corridor geometry. Ground-truth pose is shown separately for evaluation. The LIGHT control applies deterministic exposure loss and read noise before vision tracking. Relative visual motion uses a simple scale prior, so this replay is useful for repeatable tracker/UI debugging, not a validated SLAM accuracy benchmark.\n\nThe scene is synthetic rather than recorded footage, and its synchronized accelerometer/gyroscope values are simulated. Use reset and the fixed seed to compare runs reproducibly.")
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
        private final SyntheticVision vision=new SyntheticVision();
        private Bitmap cameraFrame;
        private SyntheticVision.Result visionResult;
        private int processedFrames;
        private SimulationModel.Sample sample=SimulationModel.sample(0);
        private long timeNs=0;private boolean active=true;private double speed=1;
        private final float density=getResources().getDisplayMetrics().density;
        SimulationView(){super(SimulationActivity.this);setClickable(true);}
        void setReplay(SimulationModel.Sample s,long t,boolean play,double rate){if(t+100_000_000L<timeNs){vision.reset();visionResult=null;processedFrames=0;}sample=s;timeNs=t;active=play;speed=rate;invalidate();}
        private float d(float n){return n*density;}
        private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setTextSize(d(size));paint.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,y,paint);}
        private void panel(Canvas c,float l,float t,float r,float b){paint.reset();paint.setAntiAlias(true);paint.setColor(0xE90A1B27);c.drawRoundRect(l,t,r,b,d(14),d(14),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(0x6656B8BE);c.drawRoundRect(l,t,r,b,d(14),d(14),paint);paint.setStyle(Paint.Style.FILL);}
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setStrokeWidth(d(width));paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,paint);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            if(cameraFrame==null)cameraFrame=Bitmap.createBitmap(SyntheticVision.WIDTH,SyntheticVision.HEIGHT,Bitmap.Config.ARGB_8888);
            if(processedFrames%2==0){Canvas fc=new Canvas(cameraFrame);fc.drawColor(Color.BLACK);fc.save();fc.scale(SyntheticVision.WIDTH/(float)w,SyntheticVision.HEIGHT/(float)h);drawVideo(fc,w,h);fc.restore();int[] pixels=new int[SyntheticVision.WIDTH*SyntheticVision.HEIGHT];cameraFrame.getPixels(pixels,0,SyntheticVision.WIDTH,0,0,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);applyExposure(pixels);cameraFrame.setPixels(pixels,0,SyntheticVision.WIDTH,0,0,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);visionResult=vision.process(pixels,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);}
            processedFrames++;c.drawColor(0xFF101E27);c.drawBitmap(cameraFrame,null,new RectF(0,0,w,h),null);drawTrackingOverlay(c,w,h);drawTop(c,w);drawMap(c,w,h);drawTelemetry(c,w,h);
        }
        private void applyExposure(int[] pixels){float exposure=EXPOSURES[lightIndex];if(exposure>=.999f)return;int frame=processedFrames;for(int i=0;i<pixels.length;i++){int p=pixels[i];int noise=exposure<.2f?(((i*1103515245+frame*12345)>>>28)&15):0;int r=Math.min(255,Math.round(((p>>16)&255)*exposure)+noise),g=Math.min(255,Math.round(((p>>8)&255)*exposure)+noise),b=Math.min(255,Math.round((p&255)*exposure)+noise);pixels[i]=(p&0xff000000)|(r<<16)|(g<<8)|b;}}
        private void drawTrackingOverlay(Canvas c,int w,int h){if(visionResult==null)return;for(SyntheticVision.Point p:visionResult.tracked){float x=p.x*w/SyntheticVision.WIDTH,y=p.y*h/SyntheticVision.HEIGHT;paint.reset();paint.setAntiAlias(true);paint.setColor(0xFF72F2B5);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1.4f));c.drawCircle(x,y,d(4),paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,d(1.5f),paint);}text(c,"TRACKED FEATURES  "+visionResult.trackedCount,d(14),h-d(224),9,0xFF72F2B5,true);}
        private void drawVideo(Canvas c,int w,int h){
            float horizon=h*.39f-(float)(sample.pitch*h*.65);paint.setShader(new android.graphics.LinearGradient(0,0,0,horizon,0xFF334959,0xFF9AA6A3,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,horizon,paint);paint.setShader(null);
            quad(c,-5,0,-6,5,0,-6,5,0,6,-5,0,6,0xFF697976,0xFF8FA19B);
            quad(c,-5,0,-6,-5,0,6,-5,2.85,6,-5,2.85,-6,0xFF394F59,0xFF5C6C6D);
            quad(c,5,0,-6,5,0,6,5,2.85,6,5,2.85,-6,0xFF3E525B,0xFF596A6C);
            quad(c,-5,0,-6,5,0,-6,5,2.85,-6,-5,2.85,-6,0xFF4A5D63,0xFF63777B);
            for(int i=-5;i<=5;i++)worldLine(c,i*.8,0,-6,i*.8,0,6,0x6679A49B,.7f);
            for(int i=-6;i<=6;i++)worldLine(c,-5,0,i,5,0,i,0x6679A49B,.7f);
            // Central courtyard walls make a continuous square corridor around the loop.
            quad(c,-2.05,0,-2.05,-2.05,0,2.05,-2.05,2.65,2.05,-2.05,2.65,-2.05,0xFF3E555D,0xFF728387);
            quad(c,2.05,0,2.05,2.05,0,-2.05,2.05,2.65,-2.05,2.05,2.65,2.05,0xFF435A61,0xFF728387);
            quad(c,-2.05,0,-2.05,2.05,0,-2.05,2.05,2.65,-2.05,-2.05,2.65,-2.05,0xFF465A60,0xFF728387);
            quad(c,2.05,0,2.05,-2.05,0,2.05,-2.05,2.65,2.05,2.05,2.65,2.05,0xFF40565D,0xFF728387);
            for(int z=-4;z<=4;z+=2){wallArtX(c,4.96,z,1.05,2.05,0xFF3B6268);wallArtX(c,-4.96,z,1.05,2.05,0xFF3A5C63);wallArtX(c,2.06,z,1.0,1.95,0xFF536B69);wallArtX(c,-2.06,z,1.0,1.95,0xFF536B69);}
            wallArt(c,-1.7,-5.96,1.1,.8,0xFF2F5964);wallArt(c,2.1,-5.96,.72,1.35,0xFFDAA76E);
            wallArt(c,-1.8,5.96,1.05,.8,0xFF315662);wallArt(c,2.0,5.96,.74,1.3,0xFFD6A46D);
            for(int i=-1;i<=1;i++){light(c,i*1.7,2.83,-3.9);light(c,i*1.7,2.83,3.9);light(c,-3.9,2.83,i*1.7);light(c,3.9,2.83,i*1.7);}
        }
        private void drawTop(Canvas c,int w){
            paint.reset();paint.setShader(new android.graphics.LinearGradient(0,0,0,d(147),0xF0081722,0x00081722,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,d(147),paint);paint.setShader(null);
            text(c,"VECTOR  /  SIMULATION",d(18),d(40),17,0xFFF1FAFF,true);
            text(c,"SQUARE LOOP · IMAGE-ONLY MAPPING · DETERMINISTIC",d(18),d(61),9,0xFF74FCE1,true);
            float progress=(float)(timeNs/(double)SimulationModel.DURATION_NS);String state=active?"PLAYING":"PAUSED";
            text(c,state+"  ·  "+String.format(Locale.US,"%.1f×   ·   %02d:%02d / 00:40",speed,(int)(timeNs/1e9)/60,(int)(timeNs/1e9)%60),d(18),d(84),10,0xFFF1FAFF,true);
            box.set(d(18),d(96),getWidth()-d(18),d(99));paint.reset();paint.setColor(0x553D7280);c.drawRoundRect(box,d(3),d(3),paint);box.right=box.left+(getWidth()-d(36))*progress;paint.setColor(0xFF74FCE1);c.drawRoundRect(box,d(3),d(3),paint);
        }
        private void drawMap(Canvas c,int w,int h){
            float l=w-d(155),t=d(115),r=w-d(14),b=t+d(147);panel(c,l,t,r,b);text(c,"RELATIVE VISUAL MAP",l+d(10),t+d(19),8,0xFFAAC0CC,true);
            float cx=(l+r)*.5f,cy=t+d(78),maxExtent=1f;if(visionResult!=null)for(float[] q:visionResult.path)maxExtent=Math.max(maxExtent,Math.max(Math.abs(q[0]),Math.abs(q[1])));float scale=Math.min(d(15),d(53)/maxExtent);
            if(visionResult!=null){Path path=new Path();for(int i=0;i<visionResult.path.size();i++){float[] q=visionResult.path.get(i);float x=cx+q[0]*scale,z=cy+q[1]*scale;if(i==0)path.moveTo(x,z);else path.lineTo(x,z);}paint.reset();paint.setAntiAlias(true);paint.setColor(0xFF55D9D8);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1.5f));c.drawPath(path,paint);
                for(int i=Math.max(0,visionResult.points.size()-180);i<visionResult.points.size();i++){SyntheticVision.MapPoint p=visionResult.points.get(i);paint.setStyle(Paint.Style.FILL);paint.setColor(p.depth<2?0xFFFFC976:0xFF65CBE3);c.drawCircle(cx+p.x*scale,cy+p.z*scale,d(1.5f),paint);}
                paint.setColor(0xFFFFCA75);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+visionResult.x*scale,cy+visionResult.z*scale,d(4),paint);}
            text(c,"TRACKED "+(visionResult==null?0:visionResult.trackedCount)+" · FEATURES "+(visionResult==null?0:visionResult.points.size()),l+d(10),b-d(10),7,0xFFAAC0CC,false);
        }
        private void drawTelemetry(Canvas c,int w,int h){
            float top=h-d(212),left=d(14),right=w-d(14),bottom=h-d(72);panel(c,left,top,right,bottom);
            text(c,"SYNCHRONIZED SYNTHETIC IMU",left+d(13),top+d(23),10,0xFF74FCE1,true);
            text(c,String.format(Locale.US,"ACCEL   %+.2f   %+.2f   %+.2f  m/s²",sample.ax,sample.ay,sample.az),left+d(13),top+d(48),10,0xFFF1FAFF,false);
            text(c,String.format(Locale.US,"GYRO    %+.3f   %+.3f   %+.3f  rad/s",sample.gx,sample.gy,sample.gz),left+d(13),top+d(69),10,0xFFF1FAFF,false);
            text(c,String.format(Locale.US,"TRUTH   %+.2f  %+.2f  %+.2f m   ·   YAW %+.0f°",sample.x,sample.y,sample.z,Math.toDegrees(sample.yaw)),left+d(13),top+d(90),9,0xFFAAC0CC,false);
            float mid=top+d(116);line(c,left+d(13),mid,right-d(13),mid,0x554F7684,1);
            float t=(float)(timeNs*1e-9);for(int i=1;i<44;i++){float f0=(i-1)/43f,f1=i/43f;float x0=left+d(13)+(right-left-d(26))*f0,x1=left+d(13)+(right-left-d(26))*f1;float a0=(float)(Math.sin((t-(1-f0)*2)*2*Math.PI*2.05)+.22*Math.sin((t-(1-f0)*2)*2*Math.PI*8.3));float a1=(float)(Math.sin((t-(1-f1)*2)*2*Math.PI*2.05)+.22*Math.sin((t-(1-f1)*2)*2*Math.PI*8.3));line(c,x0,mid-a0*d(6),x1,mid-a1*d(6),0xFFFFC976,1.1f);}
            text(c,"60 Hz IMU · vision "+(visionResult==null?"initializing":visionResult.trackedCount+" tracked / "+visionResult.points.size()+" mapped")+" · seed "+Long.toHexString(SimulationModel.SEED),left+d(13),bottom-d(10),8,0xFFAAC0CC,false);
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
        private void wallArtX(Canvas c,double x,double z,double width,double height,int color){quad(c,x,.05,z-width/2,x,.05,z+width/2,x,height,z+width/2,x,height,z-width/2,color,0xFFB7C5BC);}
        private void light(Canvas c,double x,double y,double z){float[] q=project(x,y,z);if(q!=null){paint.reset();paint.setAntiAlias(true);paint.setColor(0xFFFFE8B3);c.drawCircle(q[0],q[1],d(3),paint);}}
    }
}
