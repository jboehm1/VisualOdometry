package dev.jboehm.vectorvio;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/**
 * Minimal durable checkpoint shared across :basalt and the primary app process.
 *
 * A C++ SIGABRT cannot be caught. A sync'd last-stage marker makes an
 * unexpected Android activity/process death diagnosable without fabricating
 * a stack trace. No GPS/camera frames/user identity or raw IMU is persisted.
 */
public final class BasaltIncidentJournal {
    private static final String NAME="basalt_last_stage.txt";
    private static final int MAX_BYTES=512;
    private BasaltIncidentJournal(){}
    private static File file(Context context){return new File(context.getFilesDir(),NAME);}
    public static void mark(Context context,String stage){
        if(context==null||stage==null)return;
        String safe=stage.toUpperCase(java.util.Locale.US).replaceAll("[^A-Z0-9_:-]","_");
        if(safe.length()>120)safe=safe.substring(0,120);
        byte[] data=(safe+"\n"+System.currentTimeMillis()+"\n").getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream out=new FileOutputStream(file(context),false)){
            out.write(data);out.flush();out.getFD().sync();
        }catch(IOException ignored){/* journal never blocks core functionality */}
    }
    public static String read(Context context){
        if(context==null)return "UNKNOWN";
        File f=file(context);
        if(!f.isFile()||f.length()>MAX_BYTES)return "UNKNOWN";
        byte[] data=new byte[MAX_BYTES];
        try(FileInputStream in=new FileInputStream(f)){
            int n=in.read(data);
            if(n<=0)return "UNKNOWN";
            String first=new String(data,0,n,StandardCharsets.UTF_8).split("\n",2)[0].trim();
            return first.matches("[A-Z0-9_:-]{1,120}")?first:"UNKNOWN";
        }catch(IOException ignored){return "UNKNOWN";}
    }
}
