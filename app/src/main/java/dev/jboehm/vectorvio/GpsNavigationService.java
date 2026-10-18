package dev.jboehm.vectorvio;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.os.*;
import dev.jboehm.vectorvio.gps.*;
import java.io.*;
import java.util.*;

/** GPS-only foreground service: no camera/ARCore/wake lock/network upload. */
public final class GpsNavigationService extends Service implements LocationListener {
    public static final String START="NAV_START",STOP="NAV_STOP",JOIN="NAV_JOIN",SELECT="NAV_SELECT",RECORD="NAV_RECORD",SAVE="NAV_SAVE";
    public static final class State {
        public final GpsRoute route;public final GpsNavigator.Fix fix;public final GpsNavigator.Status status;
        public final double progress,off,bearing;public final boolean running,recording;public final int recorded;
        public final String message;
        State(GpsRoute route,GpsNavigator.Fix fix,GpsNavigator.Status status,double progress,double off,double bearing,boolean running,boolean recording,int recorded,String message){this.route=route;this.fix=fix;this.status=status;this.progress=progress;this.off=off;this.bearing=bearing;this.running=running;this.recording=recording;this.recorded=recorded;this.message=message;}
    }
    public static volatile boolean running,activeReverse,activeVibration;
    private static volatile GpsNavigationService owner;
    private boolean active;
    public static volatile State latest=new State(null,null,GpsNavigator.Status.PAUSED,0,Double.NaN,Double.NaN,false,false,0,"");
    private HandlerThread thread;private Handler worker;private LocationManager locations;
    private GpsNavigator nav;private GpsNavigator.Fix fix;private GpsTrackJournal journal;
    private String routeId,message="";private boolean reverse,vibrate,stopping;private long lastPersist,lastVibration;
    private int recorded;
    private static final int ID=42;
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();owner=this;thread=new HandlerThread("GpsNavigation");thread.start();worker=new Handler(thread.getLooper());locations=(LocationManager)getSystemService(LOCATION_SERVICE);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(stopping){stopSelf();return START_NOT_STICKY;}
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();if(STOP.equals(action)){worker.post(this::stopNavigation);return START_NOT_STICKY;}
        if(!active){
            if(!START.equals(action)&&!RECORD.equals(action)){stopSelf();return START_NOT_STICKY;}
            try{
                NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("gps_navigation","Suivi GPS",NotificationManager.IMPORTANCE_LOW));
                if(Build.VERSION.SDK_INT>=29)startForeground(ID,notification("Recherche GPS…"),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(ID,notification("Recherche GPS…"));
                active=true;running=true;worker.post(()->{try{subscribe();worker.postDelayed(tick,1000);}catch(Exception e){message="GPS indisponible : "+e.getClass().getSimpleName();stopNavigation();}});
            }catch(Exception e){message="Autoriser la localisation et activer le GPS";if(owner==this)running=false;publish(false);stopSelf();return START_NOT_STICKY;}
        }
        final Intent command=new Intent(intent);worker.post(()->command(command));return START_NOT_STICKY;
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,42,new Intent(this,GpsRoutesActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,43,new Intent(this,GpsNavigationService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"gps_navigation").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("VECTOR · suivi GPS").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"ARRÊTER",stop).build()).build();
    }
    @android.annotation.SuppressLint("MissingPermission") private void subscribe(){
        boolean fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        String provider=fine&&locations.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
        if(!locations.isProviderEnabled(provider))throw new IllegalStateException("Location provider disabled");
        locations.requestLocationUpdates(provider,1000,0,this,thread.getLooper());
    }
    private void command(Intent i){if(stopping)return;try{
        switch(String.valueOf(i.getAction())){
            case START:
                String nextId=i.getStringExtra("route");boolean nextReverse=i.getBooleanExtra("reverse",false);
                GpsRoute route=new RouteRepository(this).read(nextId).get(0);if(nextReverse)route=route.reverse();GpsNavigator candidate=new GpsNavigator(route);
                if(i.getBooleanExtra("resume",false)){
                    android.content.SharedPreferences p=getSharedPreferences("gps_nav",MODE_PRIVATE);
                    if(nextId.equals(p.getString("route",""))&&nextReverse==p.getBoolean("reverse",false))candidate.selectProgress(Double.parseDouble(p.getString("progress","0")));
                }
                routeId=nextId;reverse=nextReverse;activeReverse=reverse;nav=candidate;vibrate=i.getBooleanExtra("vibrate",false);activeVibration=vibrate;
                message="";fix=null;break;
            case JOIN:if(nav!=null){nav.joinNearest();message="Recherche du segment compatible…";}break;
            case SELECT:if(nav!=null){nav.selectProgress(i.getDoubleExtra("progress",0));message="Point de reprise choisi sur la trace";}break;
            case RECORD:
                if(journal==null){File f=new File(getFilesDir(),"gps_recording.journal");if(f.exists())throw new IOException("Récupérer ou supprimer l'ancien enregistrement avant d'en créer un");journal=new GpsTrackJournal(f);recorded=0;message="Enregistrement GPS démarré";}break;
            case SAVE:saveTrack();if(nav==null&&journal==null){stopNavigation();return;}break;
        }
        publish(true);
    }catch(Exception e){message="Erreur : "+e.getMessage();if(nav==null&&journal==null)stopNavigation();else publish(true);}}
    private void saveTrack(){
        if(journal==null)return;try{journal.close();journal=null;List<GpsRoute> routes=GpsTrackJournal.recover(new File(getFilesDir(),"gps_recording.journal"));String saved=new RouteRepository(this).save(routes);new File(getFilesDir(),"gps_recording.journal").delete();message="Promenade sauvegardée : "+new RouteRepository(this).label(saved);}
        catch(Exception e){journal=null;message="Enregistrement conservé à récupérer : "+e.getMessage();}
    }
    private final Runnable tick=new Runnable(){public void run(){if(stopping||owner!=GpsNavigationService.this)return;long now=SystemClock.elapsedRealtimeNanos();if(nav!=null)nav.tick(now);
        if(now-lastPersist>5_000_000_000L){lastPersist=now;try{if(journal!=null)journal.flush();}catch(Exception e){message="Écriture GPS : "+e.getMessage();saveTrack();}
            if(nav!=null&&routeId!=null)getSharedPreferences("gps_nav",MODE_PRIVATE).edit().putString("route",routeId).putBoolean("reverse",reverse).putString("progress",Double.toString(nav.progress)).apply();
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(ID,notification(nav==null?"Enregistrement GPS · "+recorded+" points":label(nav.status)+" · "+Math.round(Math.max(0,nav.route.length-nav.progress))+" m restants"));
        }
        publish(true);worker.postDelayed(this,1000);
    }};
    @Override public void onLocationChanged(Location l){if(stopping||owner!=this)return;long now=SystemClock.elapsedRealtimeNanos();
        if(!Geo.valid(l.getLatitude(),l.getLongitude()))return;
        double speed=l.hasSpeed()&&l.hasSpeedAccuracy()&&l.getSpeedAccuracyMetersPerSecond()<=Math.max(1,l.getSpeed()*.5)?l.getSpeed():Double.NaN;
        double bearing=l.hasBearing()&&l.hasBearingAccuracy()&&l.getBearingAccuracyDegrees()<=45?l.getBearing():Double.NaN;
        fix=new GpsNavigator.Fix(l.getLatitude(),l.getLongitude(),l.hasAccuracy()?l.getAccuracy():Double.NaN,speed,bearing,l.hasAltitude()?l.getAltitude():Double.NaN,l.getElapsedRealtimeNanos());
        if(nav!=null)nav.update(fix,now);
        if(journal!=null)try{journal.append(fix,now);recorded=journal.count;}catch(Exception e){message=e.getMessage();saveTrack();}
        if(nav!=null&&vibrate&&nav.status==GpsNavigator.Status.OFF_ROUTE&&now-lastVibration>60_000_000_000L){lastVibration=now;try{((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(300,VibrationEffect.DEFAULT_AMPLITUDE));}catch(Exception ignored){}}
        publish(true);
    }
    private static String label(GpsNavigator.Status s){
        switch(s){case FOLLOWING:return "Suivi estimé";case OFF_ROUTE:return "Hors parcours";case STALE:return "Position périmée";case POOR_ACCURACY:return "GPS imprécis";case AMBIGUOUS:return "Branche ambiguë";case REACQUIRE:return "Vérification reprise";case ARRIVED:return "Zone d’arrivée";case TO_START:return "Rejoindre le départ";case OUTLIER:return "Saut GPS rejeté";default:return "Recherche GPS";}
    }
    private void publish(boolean active){if(owner!=this)return;GpsRoute r=nav==null?null:nav.route;latest=new State(r,fix,nav==null?(!active?GpsNavigator.Status.PAUSED:fix==null?GpsNavigator.Status.SEARCHING:SystemClock.elapsedRealtimeNanos()-fix.ns>5_000_000_000L?GpsNavigator.Status.STALE:!Double.isFinite(fix.accuracy)||fix.accuracy>30?GpsNavigator.Status.POOR_ACCURACY:GpsNavigator.Status.FOLLOWING):active?nav.status:GpsNavigator.Status.PAUSED,nav==null?0:nav.progress,nav==null?Double.NaN:nav.offDistance,nav==null?Double.NaN:nav.targetBearing,active,journal!=null,recorded,message);}
    private void stopNavigation(){if(stopping)return;stopping=true;try{locations.removeUpdates(this);}catch(Exception ignored){}saveTrack();if(nav!=null&&routeId!=null)getSharedPreferences("gps_nav",MODE_PRIVATE).edit().putString("route",routeId).putBoolean("reverse",reverse).putString("progress",Double.toString(nav.progress)).apply();publish(false);active=false;new Handler(Looper.getMainLooper()).post(()->{if(owner==this){stopForeground(true);running=false;}stopSelf();});thread.quitSafely();}
    @Override public void onProviderDisabled(String p){message="GPS désactivé — progression suspendue";if(nav!=null)nav.tick(SystemClock.elapsedRealtimeNanos()+6_000_000_000L);publish(true);}
    @Override public void onProviderEnabled(String p){message="GPS réactivé — attente d'une position fraîche";}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    @Override public void onDestroy(){try{locations.removeUpdates(this);}catch(Exception ignored){}if(!stopping)worker.post(this::stopNavigation);super.onDestroy();}
}
