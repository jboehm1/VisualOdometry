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
/** Small controller for independent screen-off collection. No camera preview or ARCore. */
public final class CollectionActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView status;private CheckBox high;private File exportDirectory;
    private final Runnable refresh=new Runnable(){public void run(){status.setText(CollectionService.status+"\n\n"+(CollectionService.currentDirectory==null?"":CollectionService.currentDirectory.getName()));high.setEnabled(!CollectionService.running);main.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,40,24,24);root.setBackgroundColor(0xFF0B1822);
        TextView title=text("COLLECT · video / GPS / IMU",22);root.addView(title);
        root.addView(text("Independent capture: no ARCore, map or odometer. Start here while the screen is on; then you can lock the phone. A visible notification provides STOP. Screen-off reliability needs a short test on your phone before a long trip.",15));
        high=new CheckBox(this);high.setText("1080p / 6 Mbps (≈2.7 GB/h video)\nUnchecked: 720p / 3 Mbps (≈1.35 GB/h)");high.setTextColor(Color.WHITE);root.addView(high);
        root.addView(text("200 Hz requested per IMU sensor; GPS about 1 Hz when available. Sensor logs use lossless gzip; H.264 video is lossy. No audio. Video parts: up to 10 min / 256 MiB, with possible gaps at changes. Stops on low storage. No automatic restart after an error.",14));
        status=text("Stopped",17);root.addView(status,new LinearLayout.LayoutParams(-1,(int)(90*getResources().getDisplayMetrics().density)));
        button(root,"START COLLECTION",()->permissions());
        button(root,"STOP AND FINALIZE",()->{if(CollectionService.running)startService(new Intent(this,CollectionService.class).setAction(CollectionService.STOP));});
        button(root,"EXPORT A COLLECTION ZIP",this::chooseExport);
        button(root,"BACK",this::finish);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,8,0,8);return t;}
    private void button(LinearLayout parent,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());parent.addView(b);}
    private void permissions(){if(CollectionService.running)return;
        ArrayList<String> need=new ArrayList<>();for(String p:new String[]{Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)need.add(p);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)need.add(Manifest.permission.POST_NOTIFICATIONS);
        if(need.isEmpty())start();else requestPermissions(need.toArray(new String[0]),91);
    }
    private void start(){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Camera permission required",Toast.LENGTH_LONG).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Allow notifications to keep the recording STOP control visible",Toast.LENGTH_LONG).show();return;}
        try{startForegroundService(new Intent(this,CollectionService.class).putExtra("high",high.isChecked()));}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==91)start();}
    @Override public void onResume(){super.onResume();main.post(refresh);}
    @Override public void onPause(){main.removeCallbacks(refresh);super.onPause();}
    private void chooseExport(){
        if(CollectionService.running){Toast.makeText(this,"STOP and wait for finalization before export",Toast.LENGTH_LONG).show();return;}
        File root=new File(getFilesDir(),"collections");File[] sessions=root.listFiles(f->f.isDirectory()&&CollectionFiles.isSession(f.getName()));
        if(sessions==null||sessions.length==0){Toast.makeText(this,"No collections yet",Toast.LENGTH_LONG).show();return;}
        Arrays.sort(sessions,(a,b)->b.getName().compareTo(a.getName()));String[] names=new String[sessions.length];for(int i=0;i<names.length;i++)names[i]=sessions[i].getName();
        new AlertDialog.Builder(this).setTitle("Collection sessions · UTC").setItems(names,(d,i)->{exportDirectory=sessions[i];Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,exportDirectory.getName()+".zip");startActivityForResult(intent,92);}).show();
    }
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=92||result!=RESULT_OK||data==null||data.getData()==null)return;
        final File dir=exportDirectory;final android.net.Uri destination=data.getData();status.setText("Exporting… keep this screen open");
        io.execute(()->{String message;try(OutputStream out=getContentResolver().openOutputStream(destination)){if(out==null)throw new IOException("No export stream");CollectionFiles.zip(dir,out);message="Collection ZIP exported";}catch(Exception e){message="Export failed: "+e.getMessage();}final String value=message;main.post(()->Toast.makeText(this,value,Toast.LENGTH_LONG).show());});
    }
    @Override public void onDestroy(){io.shutdown();super.onDestroy();}
}
