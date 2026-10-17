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
import java.util.ArrayList;
import java.util.Locale;

/** Offline generated camera-video and synchronized deterministic IMU replay. */
public final class SimulationActivity extends Activity {
    private static final int EXPORT_REQUEST=501;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final long[] speeds={500_000_000L,1_000_000_000L,1_500_000_000L};
    private int speedIndex=1;
    private boolean playing=false,loop=true;
    private SimulationModel.Route route=SimulationModel.DEFAULT_ROUTE;
    private long baseSimNs=0,baseClockNs,stopAtNs=SimulationModel.DURATION_NS;
    private SimulationView simulationView;
    private TextView playButton,speedButton,loopButton,lightButton;
    private int lightIndex=0;
    private static final float[] EXPOSURES={1f,.38f,.14f,.045f};
    private final Runnable render=new Runnable(){@Override public void run(){
        if(!playing&&!loop&&baseSimNs>=stopAtNs)baseSimNs=stopAtNs-1;
        long time=currentTime();
        if(!loop&&time>=stopAtNs){time=stopAtNs-1;baseSimNs=time;playing=false;}
        simulationView.setReplay(SimulationModel.sample(time,route),time,playing,speeds[speedIndex]/1e9);
        if(playButton!=null)playButton.setText(playing?"PAUSE":"START");
        handler.postDelayed(this,33);
    }};

    private long currentTime(){
        long time=baseSimNs+(playing?(long)((SystemClock.elapsedRealtimeNanos()-baseClockNs)*(speeds[speedIndex]/1e9)):0);
        return loop?time:Math.min(time,stopAtNs);
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
        playButton=button("START");speedButton=button("1.0×");TextView reset=button("RESET");loopButton=button("LOOP ON");lightButton=button("LIGHT 100%");TextView export=button("CSV");
        controls.addView(playButton,weighted());controls.addView(speedButton,weighted());controls.addView(reset,weighted());controls.addView(loopButton,weighted());controls.addView(lightButton,weighted());controls.addView(export,weighted());
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM);root.addView(controls,cp);
        TextView sceneButton=button("MAP A · ↻");
        FrameLayout.LayoutParams sceneLayout=new FrameLayout.LayoutParams(dp(142),dp(38),Gravity.TOP|Gravity.RIGHT);
        sceneLayout.topMargin=dp(270);sceneLayout.rightMargin=dp(14);root.addView(sceneButton,sceneLayout);
        sceneButton.setOnClickListener(v->{route=route==SimulationModel.DEFAULT_ROUTE?SimulationModel.REVERSE_VALIDATION_ROUTE:SimulationModel.DEFAULT_ROUTE;sceneButton.setText(route.reverse?"MAP B · ↺":"MAP A · ↻");stopAtNs=route.durationNs;baseSimNs=0;baseClockNs=SystemClock.elapsedRealtimeNanos();playing=false;simulationView.resetMapping();tickNow();});
        playButton.setOnClickListener(v->{if(playing){baseSimNs=currentTime();playing=false;}else{baseClockNs=SystemClock.elapsedRealtimeNanos();playing=true;}tickNow();});
        speedButton.setOnClickListener(v->{baseSimNs=currentTime();baseClockNs=SystemClock.elapsedRealtimeNanos();speedIndex=(speedIndex+1)%speeds.length;speedButton.setText(String.format(Locale.US,"%.1f×",speeds[speedIndex]/1e9));tickNow();});
        reset.setOnClickListener(v->{stopAtNs=route.durationNs;baseSimNs=0;baseClockNs=SystemClock.elapsedRealtimeNanos();playing=false;simulationView.resetMapping();tickNow();});
        loopButton.setOnClickListener(v->{baseSimNs=currentTime();baseClockNs=SystemClock.elapsedRealtimeNanos();loop=!loop;if(!loop)stopAtNs=(baseSimNs/route.durationNs+1)*route.durationNs;loopButton.setText(loop?"LOOP ON":"LOOP OFF");});
        lightButton.setTextSize(8);lightButton.setOnClickListener(v->{lightIndex=(lightIndex+1)%EXPOSURES.length;lightButton.setText(new String[]{"LIGHT 100%","LIGHT 38%","LIGHT 14%","DARK 4%"}[lightIndex]);simulationView.lastVideoTimeNs=-1;simulationView.invalidate();});
        export.setOnClickListener(v->exportCsv());
        simulationView.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("About this simulation")
            .setMessage("Two deterministic closed corridors are rendered locally: A is a 40-second rounded-square loop; B has different rectangular dimensions, speed, texture and reverse traversal over 50 seconds. The loop has curved corners and continuous outer and inner walls beneath a continuous ceiling. The camera path has reduced handheld sway. The mapping estimator starts empty and receives only rendered camera pixels plus synchronized synthetic accelerometer and gyroscope samples. It removes calibrated gyro rotation from image feature motion, rejects tracks with a forward/backward check, initializes metric scale during turns under a forward-planar motion assumption, and creates 3D landmarks by triangulating repeated observations after estimated camera motion. The hidden corridor geometry and true camera pose are used only to render the video and to show a separate evaluation reference; they are never passed to the estimator.\n\nThe LIGHT control applies deterministic exposure loss and read noise before tracking. Before turn initialization, monocular scale uses a depth prior and the simulated sensor stream is not measured hardware data, so this is a repeatable pipeline/UI test, not a validated SLAM accuracy benchmark. The scene is synthetic rather than recorded footage. Use reset and the fixed seed to compare runs reproducibly.")
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
                csv.append("# VECTOR deterministic synthetic walk v1, seed=").append(Long.toHexString(SimulationModel.SEED)).append(", sample_rate_hz=").append(SimulationModel.SAMPLE_RATE_HZ).append(", duration_s="+(route.durationNs/1_000_000_000L)+"\n");
                csv.append("time_ns,x_m,y_m,z_m,yaw_rad,pitch_rad,roll_rad,accel_x_m_s2,accel_y_m_s2,accel_z_m_s2,gyro_x_rad_s,gyro_y_rad_s,gyro_z_rad_s\n");
                for(int i=0;i<SimulationModel.SAMPLE_RATE_HZ*(route.durationNs/1_000_000_000L);i++){
                    SimulationModel.Sample s=SimulationModel.sample(i*1_000_000_000L/SimulationModel.SAMPLE_RATE_HZ,route);
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
        private int processedFrames;private long lastVideoTimeNs=-1;
        private SimulationModel.Sample sample=SimulationModel.sample(0,route);
        private long timeNs=0;private boolean active=false;private double speed=1;
        private final float density=getResources().getDisplayMetrics().density;
        SimulationView(){super(SimulationActivity.this);setClickable(true);}
        void setReplay(SimulationModel.Sample s,long t,boolean play,double rate){if(t+100_000_000L<timeNs){vision.reset();visionResult=null;processedFrames=0;lastVideoTimeNs=-1;}sample=s;timeNs=t;active=play;speed=rate;invalidate();}
        void resetMapping(){vision.reset();visionResult=null;processedFrames=0;cameraFrame=null;lastVideoTimeNs=-1;invalidate();}
        private float d(float n){return n*density;}
        private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setTextSize(d(size));paint.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,y,paint);}
        private void panel(Canvas c,float l,float t,float r,float b){paint.reset();paint.setAntiAlias(true);paint.setColor(0xE90A1B27);c.drawRoundRect(l,t,r,b,d(14),d(14),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(0x6656B8BE);c.drawRoundRect(l,t,r,b,d(14),d(14),paint);paint.setStyle(Paint.Style.FILL);}
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setStrokeWidth(d(width));paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,paint);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            if(cameraFrame==null)cameraFrame=Bitmap.createBitmap(SyntheticVision.WIDTH,SyntheticVision.HEIGHT,Bitmap.Config.ARGB_8888);
            if(lastVideoTimeNs<0||timeNs-lastVideoTimeNs>=66_000_000L){
                int[] pixels=CorridorRenderer.render(sample,EXPOSURES[lightIndex],route);
                cameraFrame.setPixels(pixels,0,SyntheticVision.WIDTH,0,0,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);
                if(active){SyntheticVision.SensorSample sensors=new SyntheticVision.SensorSample(timeNs,sample.ax,sample.ay,sample.az,sample.gx,sample.gy,sample.gz);visionResult=vision.process(pixels,SyntheticVision.WIDTH,SyntheticVision.HEIGHT,sensors);}
                lastVideoTimeNs=timeNs;
            }
            processedFrames++;c.drawColor(0xFF101E27);c.drawBitmap(cameraFrame,null,new RectF(0,0,w,h),null);drawTrackingOverlay(c,w,h);drawTop(c,w);drawMap(c,w,h);drawTelemetry(c,w,h);
        }
        private void drawTrackingOverlay(Canvas c,int w,int h){if(visionResult==null)return;for(SyntheticVision.Point p:visionResult.tracked){float x=p.x*w/SyntheticVision.WIDTH,y=p.y*h/SyntheticVision.HEIGHT;paint.reset();paint.setAntiAlias(true);paint.setColor(0xFF72F2B5);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1.4f));c.drawCircle(x,y,d(4),paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,d(1.5f),paint);}text(c,"TRACKED FEATURES  "+visionResult.trackedCount,d(14),h-d(224),9,0xFF72F2B5,true);}
        private void drawTop(Canvas c,int w){
            paint.reset();paint.setShader(new android.graphics.LinearGradient(0,0,0,d(147),0xF0081722,0x00081722,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,d(147),paint);paint.setShader(null);
            text(c,"VECTOR  /  SIMULATION",d(18),d(40),17,0xFFF1FAFF,true);
            text(c,"ROUNDED LOOP · VIDEO + IMU · EMPTY-START 3D MAP",d(18),d(61),9,0xFF74FCE1,true);
            long lapTime=timeNs%route.durationNs;float progress=(float)(lapTime/(double)route.durationNs);String state=active?"PLAYING · ESTIMATING":"PAUSED · ESTIMATOR STOPPED";
            text(c,state+"  ·  "+String.format(Locale.US,"%.1f× · %02d:%02d / %02d:%02d",speed,(int)(lapTime/1e9)/60,(int)(lapTime/1e9)%60,(int)(route.durationNs/1e9)/60,(int)(route.durationNs/1e9)%60),d(18),d(84),10,0xFFF1FAFF,true);
            box.set(d(18),d(96),getWidth()-d(18),d(99));paint.reset();paint.setColor(0x553D7280);c.drawRoundRect(box,d(3),d(3),paint);box.right=box.left+(getWidth()-d(36))*progress;paint.setColor(0xFF74FCE1);c.drawRoundRect(box,d(3),d(3),paint);
        }
        private void drawMap(Canvas c,int w,int h){
            float l=w-d(155),t=d(115),r=w-d(14),b=t+d(147);panel(c,l,t,r,b);text(c,"LIVE 3D MAP · ESTIMATED",l+d(10),t+d(19),8,0xFFAAC0CC,true);
            float cx=(l+r)*.5f,cy=t+d(79),maxExtent=.8f;
            boolean ready=visionResult!=null&&(visionResult.trackedCount>=5||!visionResult.path.isEmpty()||!visionResult.points.isEmpty());
            if(ready){for(float[] q:visionResult.path)maxExtent=Math.max(maxExtent,Math.max(Math.abs(q[0]),Math.max(Math.abs(q[1]),Math.abs(q[2]))));for(SyntheticVision.MapPoint p:visionResult.points)maxExtent=Math.max(maxExtent,Math.max(Math.abs(p.x),Math.max(Math.abs(p.y),Math.abs(p.z))));}
            float scale=Math.min(d(15),d(46)/maxExtent);
            if(ready){
                // Isometric projection retains the vertical landmark coordinate so this is a
                // small 3D map, not a plan view of the simulator's hidden corridor.
                Path path=new Path();for(int i=0;i<visionResult.path.size();i++){float[] q=visionResult.path.get(i);float x=cx+(q[0]-q[2])*.70f*scale,y=cy+(q[0]+q[2])*.34f*scale-q[1]*scale;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                paint.reset();paint.setAntiAlias(true);paint.setColor(0xFF55D9D8);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1.5f));c.drawPath(path,paint);
                for(int i=Math.max(0,visionResult.points.size()-220);i<visionResult.points.size();i++){SyntheticVision.MapPoint p=visionResult.points.get(i);float x=cx+(p.x-p.z)*.70f*scale,y=cy+(p.x+p.z)*.34f*scale-p.y*scale;paint.setStyle(Paint.Style.FILL);paint.setColor(p.y>.25f?0xFFFFC976:0xFF65CBE3);c.drawCircle(x,y,d(1.7f),paint);}
                float px=cx+(visionResult.x-visionResult.z)*.70f*scale,py=cy+(visionResult.x+visionResult.z)*.34f*scale-visionResult.y*scale;
                paint.setColor(0xFFFFCA75);paint.setStyle(Paint.Style.FILL);c.drawCircle(px,py,d(4),paint);
            } else text(c,"WAITING FOR IMAGE TRACKS",l+d(12),cy,7,0xFFAAC0CC,true);
            text(c,"LAP "+(timeNs/route.durationNs+1)+" · 3D LANDMARKS "+(visionResult==null?0:visionResult.points.size()),l+d(10),b-d(10),7,0xFFAAC0CC,false);
        }
        private void drawTelemetry(Canvas c,int w,int h){
            float top=h-d(212),left=d(14),right=w-d(14),bottom=h-d(72);panel(c,left,top,right,bottom);
            text(c,visionResult==null?"EMPTY MAP · PRESS START":String.format(Locale.US,"EST. %.2f m · %.2f m/s · %s",visionResult.distance,visionResult.speed,visionResult.metricInitialized?"TURN SCALE":"SCALE INITIALIZING"),left+d(13),top+d(23),9,0xFF74FCE1,true);
            text(c,String.format(Locale.US,"ACCEL   %+.2f   %+.2f   %+.2f  m/s²",sample.ax,sample.ay,sample.az),left+d(13),top+d(48),10,0xFFF1FAFF,false);
            text(c,String.format(Locale.US,"GYRO    %+.3f   %+.3f   %+.3f  rad/s",sample.gx,sample.gy,sample.gz),left+d(13),top+d(69),10,0xFFF1FAFF,false);
            text(c,String.format(Locale.US,"TRUTH   %+.2f  %+.2f  %+.2f m   ·   YAW %+.0f°",sample.x,sample.y,sample.z,Math.toDegrees(sample.yaw)),left+d(13),top+d(90),9,0xFFAAC0CC,false);
            float mid=top+d(116);line(c,left+d(13),mid,right-d(13),mid,0x554F7684,1);
            float t=(float)(timeNs*1e-9);for(int i=1;i<44;i++){float f0=(i-1)/43f,f1=i/43f;float x0=left+d(13)+(right-left-d(26))*f0,x1=left+d(13)+(right-left-d(26))*f1;float a0=(float)(Math.sin((t-(1-f0)*2)*2*Math.PI*2.05)+.22*Math.sin((t-(1-f0)*2)*2*Math.PI*8.3));float a1=(float)(Math.sin((t-(1-f1)*2)*2*Math.PI*2.05)+.22*Math.sin((t-(1-f1)*2)*2*Math.PI*8.3));line(c,x0,mid-a0*d(6),x1,mid-a1*d(6),0xFFFFC976,1.1f);}
            text(c,"60 Hz IMU · vision "+(visionResult==null?"initializing":visionResult.trackedCount+" tracked / "+visionResult.points.size()+" mapped")+" · seed "+Long.toHexString(SimulationModel.SEED),left+d(13),bottom-d(10),8,0xFFAAC0CC,false);
        }
    }
}
