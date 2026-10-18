package dev.jboehm.vectorvio;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Visible controller attaches to an existing capture without restarting it. */
public final class CollectionActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView status;private Spinner profile;private File exportDirectory;
    private boolean returnToAr,exporting;
    private final Runnable refresh=new Runnable(){public void run(){
        if(returnToAr&&!CollectionService.running){returnToAr=false;startActivity(new Intent(CollectionActivity.this,MainActivity.class).putExtra("resume_after_collection",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return;}
        if(!exporting)status.setText((returnToAr?"Finalizing before live AR…\n":"")+CollectionService.status+"\n\n"+(CollectionService.currentDirectory==null?"":CollectionService.currentDirectory.getName()));
        profile.setEnabled(!CollectionService.running);main.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null)returnToAr=b.getBoolean("return_to_ar",false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,40,24,24);root.setBackgroundColor(0xFF0B1822);
        root.addView(text("WALK · screen-off capture",22));
        root.addView(text("Video + IMU + optional GPS, without ARCore, preview or mapping. Start here, then lock the phone. Unlock and open VECTOR or its notification to return to this controller; recording continues.",15));
        profile=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Complete · 720p30 · ≈1.35 GB/h video","Light · 720p15 · ≈0.68 GB/h video","Detail · 1080p30 · ≈2.7 GB/h video"});profile.setAdapter(adapter);
        profile.setSelection(getPreferences(MODE_PRIVATE).getInt("profile",0));root.addView(profile);
        root.addView(text("All profiles request IMU 200 Hz and GPS 1 Hz. Light reduces video frame rate and bitrate, so fast motion/detail may be less useful. Batching keeps original sensor timestamps and values. No microphone. Battery savings and screen-off reliability need a test on your phone.",14));
        root.addView(text("Live AR and screen-off recording use the same camera. Switching to AR finalizes this capture and starts a new tracking segment; no map is computed while the screen is off. Video part changes may have gaps. Do not cover the lens if you need useful images.",14));
        status=text("Stopped",16);root.addView(status);
        button(root,"START SCREEN-OFF CAPTURE",this::permissions);
        button(root,"STOP AND FINALIZE",()->{returnToAr=false;stop();});
        button(root,"FINALIZE AND RESUME LIVE AR",()->{returnToAr=true;stop();});
        button(root,"EXPORT A COLLECTION ZIP",this::chooseExport);
        button(root,"HOME · KEEP RECORDING",()->moveTaskToBack(true));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }
    private void stop(){if(CollectionService.running)startService(new Intent(this,CollectionService.class).setAction(CollectionService.STOP));}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,8,0,8);return t;}
    private void button(LinearLayout parent,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());parent.addView(b);}
    private void permissions(){if(CollectionService.running)return;
        ArrayList<String> need=new ArrayList<>();for(String p:new String[]{Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)need.add(p);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)need.add(Manifest.permission.POST_NOTIFICATIONS);
        if(need.isEmpty())start();else requestPermissions(need.toArray(new String[0]),91);
    }
    private void start(){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Camera permission required",Toast.LENGTH_LONG).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Allow notifications for capture status and STOP",Toast.LENGTH_LONG).show();return;}
        int selected=profile.getSelectedItemPosition();getPreferences(MODE_PRIVATE).edit().putInt("profile",selected).apply();
        String id=selected==1?"light":selected==2?"detail":"full";
        try{startForegroundService(new Intent(this,CollectionService.class).putExtra("profile",id));}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==91)start();}
    @Override public void onResume(){super.onResume();main.post(refresh);}
    @Override public void onPause(){main.removeCallbacks(refresh);super.onPause();}
    @Override public void onSaveInstanceState(Bundle b){b.putBoolean("return_to_ar",returnToAr);super.onSaveInstanceState(b);}
    @Override public void onBackPressed(){if(CollectionService.running)moveTaskToBack(true);else super.onBackPressed();}
    private void chooseExport(){
        if(CollectionService.running){Toast.makeText(this,"STOP and wait for finalization before export",Toast.LENGTH_LONG).show();return;}
        File root=new File(getFilesDir(),"collections");File[] sessions=root.listFiles(f->f.isDirectory()&&CollectionFiles.isSession(f.getName()));
        if(sessions==null||sessions.length==0){Toast.makeText(this,"No collections yet",Toast.LENGTH_LONG).show();return;}
        Arrays.sort(sessions,(a,b)->b.getName().compareTo(a.getName()));String[] names=new String[sessions.length];for(int i=0;i<names.length;i++)names[i]=sessions[i].getName();
        new AlertDialog.Builder(this).setTitle("Collection sessions · UTC").setItems(names,(d,i)->{exportDirectory=sessions[i];Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,exportDirectory.getName()+".zip");startActivityForResult(intent,92);}).show();
    }
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=92||result!=RESULT_OK||data==null||data.getData()==null)return;
        final File dir=exportDirectory;if(dir==null){Toast.makeText(this,"Select the collection again",Toast.LENGTH_LONG).show();return;}
        final android.net.Uri destination=data.getData();exporting=true;status.setText("Exporting… keep this screen open");
        io.execute(()->{String message;try(OutputStream out=getContentResolver().openOutputStream(destination)){if(out==null)throw new IOException("No export stream");CollectionFiles.zip(dir,out);message="Collection ZIP exported";}catch(Exception e){message="Export failed: "+e.getMessage();}final String value=message;main.post(()->{exporting=false;Toast.makeText(this,value,Toast.LENGTH_LONG).show();});});
    }
    @Override public void onDestroy(){io.shutdown();super.onDestroy();}
}
