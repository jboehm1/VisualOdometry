package dev.jboehm.vectorvio.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.*;

/** One active provider. Tokens cancel stale async subscriptions and callbacks after stop. */
public final class AndroidLocationSource implements LocationSource {
    private final Context context;
    private final Listener listener;
    private final HandlerThread thread=new HandlerThread("GpxArLocation");
    private final Handler handler;
    private final LocationManager manager;
    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private LocationListener nativeListener;
    private int generation;
    private volatile boolean closed;
    public AndroidLocationSource(Context context,Listener listener){
        this.context=context.getApplicationContext();this.listener=listener;
        manager=(LocationManager)this.context.getSystemService(Context.LOCATION_SERVICE);
        thread.start();handler=new Handler(thread.getLooper());
    }
    @Override public void start(Mode mode){if(!closed)handler.post(()->{if(closed)return;remove();int token=++generation;subscribe(mode,token);});}
    @Override public void stop(){if(!closed)handler.post(()->{++generation;remove();listener.onStatus("LOCATION OFF");});}
    @Override public void close(){closed=true;handler.post(()->{++generation;remove();thread.quitSafely();});}
    private boolean current(int token){return !closed&&token==generation;}
    private void remove(){
        if(nativeListener!=null){try{manager.removeUpdates(nativeListener);}catch(RuntimeException ignored){}nativeListener=null;}
        if(callback!=null&&fused!=null){try{fused.removeLocationUpdates(callback);}catch(RuntimeException ignored){}callback=null;}
        handler.removeCallbacksAndMessages("watchdog");
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void subscribe(Mode mode,int token){
        if(!current(token))return;
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){listener.onStatus("PRECISE LOCATION REQUIRED");return;}
        boolean available=false;
        if(mode!=Mode.GPS)try{available=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==ConnectionResult.SUCCESS;}catch(RuntimeException ignored){}
        if(mode!=Mode.GPS&&available){
            try{
                fused=LocationServices.getFusedLocationProviderClient(context);
                final long[] latest={SystemClock.elapsedRealtimeNanos()};
                final LocationCallback subscription=new LocationCallback(){
                    @Override public void onLocationResult(LocationResult result){
                        if(!current(token))return;
                        for(Location l:result.getLocations()){
                            long age=SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos();
                            if(age>=0&&age<=5_000_000_000L)latest[0]=SystemClock.elapsedRealtimeNanos();
                            accept(l,LocationSample.Source.GOOGLE_FUSED);
                        }
                    }
                };
                callback=subscription;
                LocationRequest request=new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,1000L)
                    .setMinUpdateIntervalMillis(500L).setMaxUpdateDelayMillis(0L).build();
                fused.requestLocationUpdates(request,subscription,thread.getLooper()).addOnCompleteListener(handler::post,task->{
                    if(!current(token)){try{fused.removeLocationUpdates(subscription);}catch(RuntimeException ignored){}return;}
                    if(!task.isSuccessful())fallback(mode,token,"FUSED ERROR");
                });
                listener.onStatus("GOOGLE FUSED · SEARCHING");
                if(mode==Mode.AUTO){
                    Runnable watchdog=new Runnable(){public void run(){
                        if(!current(token))return;
                        if(SystemClock.elapsedRealtimeNanos()-latest[0]>12_000_000_000L){fallback(mode,token,"FUSED STALE");return;}
                        handler.postAtTime(this,"watchdog",SystemClock.uptimeMillis()+4000);
                    }};
                    handler.postAtTime(watchdog,"watchdog",SystemClock.uptimeMillis()+4000);
                }
                return;
            }catch(RuntimeException e){fallback(mode,token,"FUSED UNAVAILABLE");return;}
        }
        if(mode==Mode.FUSED){listener.onStatus("GOOGLE PLAY SERVICES UNAVAILABLE");return;}
        nativeGps(token);
    }
    private void fallback(Mode mode,int token,String reason){
        if(!current(token))return;
        remove();int next=++generation;
        if(mode==Mode.AUTO){listener.onStatus(reason+" · GPS FALLBACK");nativeGps(next);}
        else listener.onStatus(reason);
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void nativeGps(int token){
        try{
            if(manager==null||!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){listener.onStatus("GPS DISABLED · ENABLE LOCATION");return;}
            nativeListener=new LocationListener(){
                @Override public void onLocationChanged(Location l){if(current(token))accept(l,LocationSample.Source.GPS_PROVIDER);}
                @Override public void onProviderDisabled(String p){if(current(token))listener.onStatus("GPS DISABLED");}
                @Override public void onProviderEnabled(String p){if(current(token))listener.onStatus("GPS SEARCHING");}
                @Override public void onStatusChanged(String p,int s,Bundle b){}
            };
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,nativeListener,thread.getLooper());
            listener.onStatus("GPS NATIVE · SEARCHING");
        }catch(SecurityException e){listener.onStatus("LOCATION PERMISSION LOST");remove();}
        catch(RuntimeException e){listener.onStatus("GPS UNAVAILABLE");remove();}
    }
    private void accept(Location l,LocationSample.Source source){
        listener.onSample(new LocationSample(source,l.getElapsedRealtimeNanos(),SystemClock.elapsedRealtimeNanos(),l.getTime(),
            l.getLatitude(),l.getLongitude(),l.hasAccuracy()?l.getAccuracy():Double.NaN,
            l.hasAltitude()?l.getAltitude():Double.NaN,l.hasVerticalAccuracy()?l.getVerticalAccuracyMeters():Double.NaN,
            l.hasSpeed()?l.getSpeed():Double.NaN,l.hasSpeedAccuracy()?l.getSpeedAccuracyMetersPerSecond():Double.NaN,
            l.hasBearing()?l.getBearing():Double.NaN,l.hasBearingAccuracy()?l.getBearingAccuracyDegrees():Double.NaN,l.isFromMockProvider()));
    }
}
