package dev.jboehm.vectorvio;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.*;

import dev.jboehm.vectorvio.location.LocationSample;
import dev.jboehm.vectorvio.location.LocationPairer;
import dev.jboehm.vectorvio.location.LocationComparisonRecorder;

import java.io.*;
import java.util.Locale;

/**
 * Opt-in and foreground-only A/B recording of Android GNSS and Google FLP.
 * It never changes GPX navigation, VIO poses or the independent PDR project.
 */
public final class LocationComparisonActivity extends Activity {
    private static final int PERMISSION=247,EXPORT_ZIP=248;
    private final Handler main=new Handler(Looper.getMainLooper());
    private HandlerThread sensorThread;
    private Handler worker;
    private LocationManager gpsManager;
    private FusedLocationProviderClient fusedProvider;
    private LocationListener gpsListener;
    private LocationCallback fusedCallback;
    private LocationComparisonRecorder recorder;
    private final LocationPairer pairer=new LocationPairer();
    private LocationSample lastGps,lastFused;
    private File lastSession;
    private volatile boolean running,stopping;
    private long lastUiMs;
    private TextView statusText,gpsText,fusedText,comparisonText;
    private Button startStop,exportButton;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff091723);
        getWindow().setNavigationBarColor(0xff091723);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(18),dp(18),dp(22));
        content.setBackgroundColor(0xff091723);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.addView(content);setContentView(scroll);
        content.addView(text("GPS  ↔  ANDROID FUSED",22,0xff70fade,true));
        content.addView(text("Comparaison réelle · sans caméra ni PDR",13,0xffb9d5dd,false));
        statusText=panel(content,"SESSION","Prêt · position précise requise · premier plan uniquement");
        gpsText=panel(content,"GNSS DIRECT · LocationManager","En attente");
        fusedText=panel(content,"GOOGLE FUSED · Play Services","En attente");
        comparisonText=panel(content,"DÉSACCORD ENTRE SOURCES","Aucune paire temporelle");
        startStop=button("DÉMARRER LA COMPARAISON",true);
        startStop.setOnClickListener(v->{if(running)stopComparison("USER_STOP");else requestStart();});
        content.addView(startStop);
        exportButton=button("EXPORTER LE DERNIER TEST · ZIP",false);
        exportButton.setOnClickListener(v->export());
        exportButton.setEnabled(false);content.addView(exportButton);
        Button help=button("PROTOCOLE ET LIMITES",false);
        help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("GPS versus Google FLP")
            .setMessage("Les deux sources sont enregistrées en parallèle, à environ 1 Hz demandé. Les positions sont appariées sans interpolation dans une fenêtre de 1,5 s. Seuls les fixes frais, non simulés et d'une précision annoncée de 30 m ou moins sont appariés ; tous les fixes sont conservés bruts.\n\nL'écart GPS–FLP n'est PAS une erreur de position réelle : les API peuvent partager le même signal GNSS et des biais communs.\n\nTester immobile, puis sur un trajet mesuré et sous les arbres. Garder cet écran visible. Retour/écran éteint arrête la session. Aucun GPS/VIO/PDR existant n'est modifié.")
            .setPositiveButton("OK",null).show());
        content.addView(help);
        content.addView(text("Les données restent privées jusqu'à l'export explicite. Google Play Services peut exploiter ses propres services réseau selon les réglages du téléphone.",12,0xff9cb9c2,false));
    }

    private int dp(int p){return Math.round(p*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp,int color,boolean strong){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);
        t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView panel(LinearLayout parent,String heading,String detail){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(10),dp(14),dp(12));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff122b39);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),0xff315363);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(9),0,dp(4));parent.addView(card,lp);
        card.addView(text(heading,12,0xff70fade,true));
        TextView label=text(detail,14,Color.WHITE,false);card.addView(label);return label;
    }
    private Button button(String s,boolean important){
        Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(true);
        b.setTextColor(important?0xff061723:Color.WHITE);
        GradientDrawable bg=new GradientDrawable();bg.setColor(important?0xff70fade:0xff264351);
        bg.setCornerRadius(dp(12));b.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));
        lp.setMargins(0,dp(8),0,0);b.setLayoutParams(lp);return b;
    }
    private void message(String m){if(statusText!=null)statusText.setText(m);}
    private void requestStart(){
        if(stopping){message("Fermeture de la session précédente…");return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            new AlertDialog.Builder(this).setTitle("Position précise nécessaire")
                .setMessage("Le test doit obtenir la localisation précise. Une position approximative fausserait la comparaison. Autoriser uniquement pendant l'utilisation ; aucun enregistrement en arrière-plan.")
                .setNegativeButton("ANNULER",null)
                .setPositiveButton("AUTORISER",(d,w)->requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSION)).show();
        }else startComparison();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(code,permissions,grantResults);
        if(code==PERMISSION){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startComparison();
            else message("Position précise refusée · aucune session démarrée");
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void startComparison(){
        if(running||stopping)return;
        int available=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if(available!=ConnectionResult.SUCCESS){
            message("Google Play Services indisponible (code "+available+"). Aucune substitution silencieuse.");return;
        }
        gpsManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(gpsManager==null||!gpsManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            message("Activer le GPS natif du téléphone pour lancer la comparaison.");return;
        }
        pairer.reset();lastGps=lastFused=null;lastUiMs=0;
        running=true;
        startStop.setText("ARRÊTER ET SAUVEGARDER");exportButton.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        message("Ouverture du GPS et de Google FLP…");
        sensorThread=new HandlerThread("VioLocationAB");
        sensorThread.start();worker=new Handler(sensorThread.getLooper());
        worker.post(()->{
            try{
                recorder=new LocationComparisonRecorder(new File(getFilesDir(),"location_comparisons"),
                    System.currentTimeMillis(),Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE);
                lastSession=recorder.directory();
                fusedProvider=LocationServices.getFusedLocationProviderClient(this);
                gpsListener=loc->accept(loc,LocationSample.Source.GPS_PROVIDER);
                gpsManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,
                    gpsListener,sensorThread.getLooper());
                fusedCallback=new LocationCallback(){
                    @Override public void onLocationResult(LocationResult result){
                        if(result!=null)for(Location loc:result.getLocations())
                            accept(loc,LocationSample.Source.GOOGLE_FUSED);
                    }
                };
                LocationRequest request=new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,1000L)
                    .setMinUpdateIntervalMillis(500L)
                    .setMaxUpdateDelayMillis(0L).build();
                fusedProvider.requestLocationUpdates(request,fusedCallback,sensorThread.getLooper())
                    .addOnFailureListener(error->main.post(()->{
                        if(running)stopComparison("FLP_SUBSCRIBE_ERROR:"+error.getClass().getSimpleName());
                    }));
                main.post(()->{if(running)message("Acquisition demandée à ~1 Hz · garder l'écran visible");});
            }catch(Exception failure){
                main.post(()->stopComparison("SUBSCRIPTION_ERROR:"+failure.getClass().getSimpleName()));
            }
        });
    }
    /** Called on the dedicated sensor thread, for both sources. */
    private void accept(Location location,LocationSample.Source source){
        if(!running||recorder==null||location==null)return;
        LocationSample sample=new LocationSample(source,location.getElapsedRealtimeNanos(),
            SystemClock.elapsedRealtimeNanos(),location.getTime(),location.getLatitude(),location.getLongitude(),
            location.hasAccuracy()?location.getAccuracy():Double.NaN,
            location.hasAltitude()?location.getAltitude():Double.NaN,
            location.hasVerticalAccuracy()?location.getVerticalAccuracyMeters():Double.NaN,
            location.hasSpeed()?location.getSpeed():Double.NaN,
            location.hasSpeedAccuracy()?location.getSpeedAccuracyMetersPerSecond():Double.NaN,
            location.hasBearing()?location.getBearing():Double.NaN,
            location.hasBearingAccuracy()?location.getBearingAccuracyDegrees():Double.NaN,
            location.isFromMockProvider());
        LocationPairer.Pair pair=pairer.accept(sample);
        try{recorder.record(sample,pair,System.currentTimeMillis());}
        catch(IOException e){main.post(()->stopComparison("WRITE_ERROR:"+e.getClass().getSimpleName()));return;}
        if(source==LocationSample.Source.GPS_PROVIDER)lastGps=sample;else lastFused=sample;
        long now=SystemClock.elapsedRealtime();
        if(now-lastUiMs>=750){
            lastUiMs=now;
            LocationPairer.Snapshot snapshot=pairer.snapshot();
            LocationSample gps=lastGps,fused=lastFused;
            main.post(()->{if(running)render(snapshot,gps,fused);});
        }
    }
    private static String format(LocationSample s,int count,int rejected){
        if(s==null)return count+" observations · première position attendue";
        return String.format(Locale.FRANCE,
            "%d fixes · %d exclus des paires\nPrécision annoncée ±%.1f m · âge %.0f ms\n%s\n%.6f°, %.6f°",
            count,rejected,s.accuracyMeters,s.ageMs(),s.validity(),s.latitude,s.longitude);
    }
    private void render(LocationPairer.Snapshot stats,LocationSample gps,LocationSample fused){
        gpsText.setText(format(gps,stats.gpsCount,stats.rejectedGps));
        fusedText.setText(format(fused,stats.fusedCount,stats.rejectedFused));
        if(stats.pairCount==0)comparisonText.setText("0 paire temporelle compatible · aucune conclusion");
        else comparisonText.setText(String.format(Locale.FRANCE,
            "%d paires · moyenne %.1f m · maximum %.1f m\nDécalage moyen %.0f ms\nDÉSACCORD, PAS ERREUR ABSOLUE",
            stats.pairCount,stats.meanDisagreementMeters,stats.maxDisagreementMeters,
            stats.meanPairTimeOffsetMs));
    }

    private void stopComparison(String reason){
        if(!running||stopping)return;
        running=false;stopping=true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startStop.setEnabled(false);
        message("Arrêt et clôture des CSV en cours…");
        Handler h=worker;
        if(h==null){complete("Aucune session ouverte");return;}
        h.post(()->{
            String note=reason;
            try{if(gpsManager!=null&&gpsListener!=null)gpsManager.removeUpdates(gpsListener);}
            catch(RuntimeException e){note+="; GPS_REMOVE_ERROR";}
            try{if(fusedProvider!=null&&fusedCallback!=null)fusedProvider.removeLocationUpdates(fusedCallback);}
            catch(RuntimeException e){note+="; FLP_REMOVE_ERROR";}
            try{if(recorder!=null)recorder.finish(note,pairer.snapshot(),System.currentTimeMillis());}
            catch(IOException e){note+="; RECORD_CLOSE_ERROR:"+e.getMessage();}
            String finished=note;
            LocationPairer.Snapshot stats=pairer.snapshot();
            LocationSample gps=lastGps,fused=lastFused;
            if(sensorThread!=null)sensorThread.quitSafely();
            main.post(()->{render(stats,gps,fused);complete("Session terminée : "+finished);});
        });
    }
    private void complete(String note){
        stopping=false;worker=null;sensorThread=null;gpsListener=null;fusedCallback=null;recorder=null;
        startStop.setEnabled(true);startStop.setText("NOUVEAU TEST");
        exportButton.setEnabled(lastSession!=null&&
            new File(lastSession,LocationComparisonRecorder.META_JSON).isFile());
        message(note);
    }

    private void export(){
        if(running||stopping){message("Arrêter la session avant d'exporter.");return;}
        if(lastSession==null||!new File(lastSession,LocationComparisonRecorder.META_JSON).isFile()){
            message("Aucune session finalisée à exporter.");return;
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip").putExtra(Intent.EXTRA_TITLE,lastSession.getName()+".zip");
        startActivityForResult(intent,EXPORT_ZIP);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=EXPORT_ZIP||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();File folder=lastSession;exportButton.setEnabled(false);
        message("Export privé en cours…");
        new Thread(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new IOException("Destination inaccessible");
                LocationComparisonRecorder.exportZip(folder,out);
                main.post(()->{exportButton.setEnabled(true);message("Export ZIP terminé · données locales copiées à l'emplacement choisi");});
            }catch(Exception error){
                main.post(()->{exportButton.setEnabled(true);message("Échec export : "+error.getMessage());});
            }
        },"VioLocationABExport").start();
    }

    /** Never record in background or with a locked screen. */
    @Override protected void onPause(){
        super.onPause();
        if(running)stopComparison("ACTIVITY_NOT_VISIBLE");
    }
    @Override protected void onDestroy(){
        if(running)stopComparison("ACTIVITY_DESTROYED");
        super.onDestroy();
    }
}
