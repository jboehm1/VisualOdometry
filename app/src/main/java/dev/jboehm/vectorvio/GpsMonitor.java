package dev.jboehm.vectorvio;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.Locale;
/** Optional foreground location diagnostics. Never silently fused with camera-relative VIO. */
final class GpsMonitor implements LocationListener {
    interface Listener{void fix(Location location,String status);}
    private final Context context;private final LocationManager manager;private final Listener listener;
    volatile String status="GPS OFF";boolean enabled;
    GpsMonitor(Context context,Listener listener){this.context=context;this.listener=listener;manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);}
    @android.annotation.SuppressLint("MissingPermission")
    void start(){
        if(!enabled)return;stopUpdates();status="GPS SEARCHING";
        try{
            boolean fine=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            String provider=fine&&manager.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            if(!manager.isProviderEnabled(provider)){status="GPS ENABLE LOCATION";return;}
            manager.requestLocationUpdates(provider,1000L,0f,this,Looper.getMainLooper());
        }catch(SecurityException e){status="GPS PERMISSION";}catch(Exception e){status="GPS UNAVAILABLE";}
    }
    @android.annotation.SuppressLint("MissingPermission")
    void stopUpdates(){try{manager.removeUpdates(this);}catch(Exception ignored){}}
    void disable(){enabled=false;stopUpdates();status="GPS OFF";}
    @Override public void onLocationChanged(Location location){
        double age=(SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos())*1e-9;
        if(age<0||age>5||!location.hasAccuracy()||!Float.isFinite(location.getAccuracy()))return;
        status=String.format(Locale.US,"GPS ±%.0fm · %s",location.getAccuracy(),location.hasSpeed()?String.format(Locale.US,"%.1f km/h",location.getSpeed()*3.6):"NO SPEED");
        listener.fix(location,status);
    }
    @Override public void onProviderDisabled(String provider){status="GPS DISABLED";}
    @Override public void onProviderEnabled(String provider){if(enabled)start();}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
}
