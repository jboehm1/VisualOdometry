package dev.jboehm.vectorvio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reopens saved maps entirely offline. No AR session is restored or claimed. */
public final class MapActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private MapArchive archive;private MapView map;private ImageView photo;private TextView photoLabel;
    private SeekBar slider;private boolean photos=false;
    private Bitmap bitmap;
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        TextView loading=new TextView(this);loading.setText("Opening saved map…");setContentView(loading);
        String name=getIntent().getStringExtra("map");
        if(name==null || !name.matches("map_[0-9_]+\\.vmap")){finish();return;}
        File source=new File(new File(getFilesDir(),"maps"),name);
        io.execute(()->{
            try {MapArchive loaded=MapArchive.read(source);runOnUiThread(()->{if(!isFinishing()){archive=loaded;build();}});}
            catch(Exception e){runOnUiThread(()->{Toast.makeText(this,"Cannot open map: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();});}
        });
    }
    private TextView button(String text,LinearLayout row,Runnable action) {
        TextView v=new TextView(this);v.setText(text);v.setTextColor(0xFF8DF4DF);v.setTextSize(11);v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(0xFF15303F);v.setContentDescription(text);
        int size=(int)(48*getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,size,1);p.setMargins(2,2,2,2);row.addView(v,p);v.setOnClickListener(w->action.run());return v;
    }
    private void build() {
        if(bitmap!=null){photo.setImageDrawable(null);bitmap=null;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF06121F);
        map=new MapView(this,archive);root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        photoLabel=new TextView(this);photoLabel.setTextColor(0xFFAAC9D5);photoLabel.setTextSize(12);root.addView(photoLabel);
        photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(photo,new LinearLayout.LayoutParams(-1,(int)(180*getResources().getDisplayMetrics().density)));photo.setVisibility(View.GONE);
        slider=new SeekBar(this);slider.setMax(Math.max(0,map.count()-1));
        slider.setContentDescription("Selected camera keyframe");root.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){map.setKeyframe(value);showPhoto();}
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
        });
        LinearLayout row=new LinearLayout(this);root.addView(row);
        button("SEGMENT",row,()->{map.nextSegment();slider.setMax(Math.max(0,map.count()-1));slider.setProgress(0);showPhoto();});
        button("POINTS",row,()->{map.showPoints=!map.showPoints;map.invalidate();});
        button("PATH",row,()->{map.showPath=!map.showPath;map.invalidate();});
        button("PHOTOS",row,()->{photos=!photos;showPhoto();});
        LinearLayout second=new LinearLayout(this);root.addView(second);
        button("LOOP CONSTRAINT",second,()->{
            if(archive.manualLoopConstraint){Toast.makeText(this,"Constraint already applied. Reopen the original to compare.",Toast.LENGTH_LONG).show();return;}
            int segment=map.segment();
            new AlertDialog.Builder(this).setTitle("Manual same-pose loop constraint")
                .setMessage("Use ONLY if the first and last keyframes of segment "+segment+" show the camera at the same physical position AND orientation. This assumes that closure is correct and distributes the endpoint pose discrepancy through the route and cloud. It does not recognize places, optimize feature observations, establish accuracy, or change recorded odometer measurements. The original map is retained.")
                .setNegativeButton("CANCEL",null).setPositiveButton("APPLY TO COPY",(d,w)->{
                    try {
                        archive=LoopConstraint.apply(archive,segment);build();
                        MapArchive corrected=archive;
                        File destination=new File(new File(getFilesDir(),"maps"),"map_"+System.currentTimeMillis()+".vmap");
                        io.execute(()->{try{corrected.write(destination);runOnUiThread(()->Toast.makeText(this,"Saved manually constrained copy. Original retained.",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Could not save corrected copy",Toast.LENGTH_LONG).show());}});
                    }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
                }).show();
        });
        button("EXPORT ZIP",second,()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"vector_map.zip");
            try{startActivityForResult(intent,201);}catch(Exception e){Toast.makeText(this,"No document provider",Toast.LENGTH_LONG).show();}
        });
        setContentView(root);showPhoto();
        if(archive.manualLoopConstraint)photoLabel.setText("MANUAL LOOP CONSTRAINT · assumed endpoint, not validated");
    }
    private void showPhoto() {
        photo.setVisibility(photos?View.VISIBLE:View.GONE);
        photoLabel.setText("Keyframe "+(slider.getProgress()+1)+" / "+map.count()+" · "+(photos?"sensor image orientation":"drag slider to inspect camera poses"));
        if(!photos)return;
        if(bitmap!=null){photo.setImageDrawable(null);bitmap=null;}
        byte[] bytes=map.image();
        if(bytes.length>0){bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);photo.setImageBitmap(bitmap);}
        else photoLabel.setText("No photo for this keyframe. Enable photo keyframes before capture.");
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=201 || result!=RESULT_OK || data==null || data.getData()==null)return;
        android.net.Uri target=data.getData();
        io.execute(()->{
            String message;
            try(OutputStream out=getContentResolver().openOutputStream(target)) {
                if(out==null)throw new java.io.IOException("Cannot open destination");
                archive.exportZip(out);message="Exported cloud.ply, trajectory.tum and optional photos";
            }catch(Exception e){message="Export failed: "+e.getMessage();}
            String status=message;runOnUiThread(()->Toast.makeText(this,status,Toast.LENGTH_LONG).show());
        });
    }
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
