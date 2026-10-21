package dev.jboehm.vectorvio;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Installed package version plus the source identity embedded by the build. */
public final class AppIdentity {
    private AppIdentity() {}
    public static String version(Context context) {
        try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}
        catch(Exception unavailable){return "unknown";}
    }
    public static String sourceCommit(Context context) {
        try(InputStream in=context.getAssets().open("build_provenance.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
            while((n=in.read(buffer))!=-1){
                if(bytes.size()+n>16384)return "UNKNOWN";
                bytes.write(buffer,0,n);
            }
            String sha=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)).optString("source_commit");
            return sha.matches("[0-9a-f]{40}|[0-9a-f]{64}")?sha:"UNKNOWN";
        }catch(Exception unavailable){return "UNKNOWN";}
    }
    public static String buildLabel(Context context) {
        String sha=sourceCommit(context);
        String engines="ARCore / PDR";
        try(InputStream nativeReceipt=context.getAssets().open("native_build.json")){engines+=" / Basalt expérimental";}
        catch(Exception unavailable){/* A Java-only developer build has no native backend. */}
        return "Build "+(sha.equals("UNKNOWN")?"LOCAL / NON IDENTIFIÉ":sha.substring(0,8))+" · "+engines;
    }
}
