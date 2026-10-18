package dev.jboehm.vectorvio;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import dev.jboehm.vectorvio.gps.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline GPX library, route diagram and foreground/background GPS controller. */
public final class GpsRoutesActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
    private RouteRepository repository;private GpsRoute route;private String routeId;private TextView title,status;private CheckBox reverse,vibrate;private GpsRouteView map;
    private boolean importing;private String pendingAction;private boolean pendingResume;
    private final Runnable refresh=new Runnable(){public void run(){GpsNavigationService.State s=GpsNavigationService.latest;
        reverse.setEnabled(!GpsNavigationService.running);
        if(s.running&&s.route!=null){map.setRoute(s.route);title.setText(s.route.name);}else if(route!=null){map.setRoute(reverse.isChecked()?previewReverse():route);title.setText(route.name);}
        map.setState(s);if(!importing)status.setText(describe(s));main.postDelayed(this,1000);
    }};
    private GpsRoute reversed;
    private GpsRoute previewReverse(){if(reversed==null)reversed=route.reverse();return reversed;}
    @Override public void onCreate(Bundle b){super.onCreate(b);repository=new RouteRepository(this);routeId=getPreferences(MODE_PRIVATE).getString("route",null);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(0xff0b1722);
        title=text("PARCOURS GPS",20);root.addView(title);
        map=new GpsRouteView(this);root.addView(map,new LinearLayout.LayoutParams(-1,0,1));map.setMinimumHeight(dp(180));map.setSelection(this::selectProgress);
        status=text("Importer un GPX ou enregistrer une promenade",13);root.addView(status);
        LinearLayout toggles=new LinearLayout(this);reverse=new CheckBox(this);reverse.setText("Sens inverse");reverse.setTextColor(Color.WHITE);reverse.setOnCheckedChangeListener((v,c)->{if(route!=null)map.setRoute(c?previewReverse():route);});toggles.addView(reverse);
        vibrate=new CheckBox(this);vibrate.setText("Vibration écart");vibrate.setTextColor(Color.WHITE);toggles.addView(vibrate);root.addView(toggles);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        row(controls,"IMPORT GPX",this::importGpx,"MES PARCOURS",this::chooseRoute);
        row(controls,"DÉPART",()->start(GpsNavigationService.START,false),"REPRENDRE",()->start(GpsNavigationService.START,true));
        row(controls,"REJOINDRE TRACE",()->command(GpsNavigationService.JOIN),"ARRÊTER GPS",()->command(GpsNavigationService.STOP));
        row(controls,"ENREGISTRER",()->start(GpsNavigationService.RECORD,false),"SAUVER PROMENADE",()->command(GpsNavigationService.SAVE));
        row(controls,"EXPORT GPX",this::exportGpx,"AJUSTER VUE",map::fit);
        row(controls,"RÉCUPÉRER TRACE",this::recover,"AIDE",this::help);
        ScrollView buttons=new ScrollView(this);buttons.addView(controls);root.addView(buttons,new LinearLayout.LayoutParams(-1,dp(225)));setContentView(root);
        if(routeId!=null)load(routeId);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(4),0,dp(4));return t;}
    private void row(LinearLayout root,String a,Runnable aa,String b,Runnable bb){LinearLayout r=new LinearLayout(this);for(int i=0;i<2;i++){Button button=new Button(this);button.setText(i==0?a:b);button.setTextSize(10);final Runnable action=i==0?aa:bb;button.setOnClickListener(v->action.run());r.addView(button,new LinearLayout.LayoutParams(0,dp(44),1));}root.addView(r);}
    private boolean canChoose(){if(GpsNavigationService.running){toast("Arrêter le suivi GPS avant de changer de parcours");return false;}return true;}
    private void importGpx(){if(!canChoose())return;startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),101);}
    private void chooseRoute(){if(!canChoose())return;io.execute(()->{List<String> ids=repository.list();String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=repository.label(ids.get(i));main.post(()->{if(isFinishing()||isDestroyed())return;if(ids.isEmpty()){toast("Aucun parcours enregistré");return;}new AlertDialog.Builder(this).setTitle("Parcours sur ce téléphone").setItems(names,(d,i)->{new AlertDialog.Builder(this).setTitle(names[i]).setItems(new String[]{"Ouvrir","Supprimer"},(x,n)->{if(n==0)load(ids.get(i));else new AlertDialog.Builder(this).setMessage("Supprimer ce parcours ?").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(q,w)->io.execute(()->{try{repository.delete(ids.get(i));main.post(()->{if(ids.get(i).equals(routeId)){route=null;routeId=null;reversed=null;map.setRoute(null);getPreferences(MODE_PRIVATE).edit().remove("route").apply();}toast("Parcours supprimé");});}catch(Exception e){error(e);}})).show();}).show();}).show();});});}
    private void load(String id){importing=true;status.setText("Chargement…");io.execute(()->{try{List<GpsRoute> routes=repository.read(id);main.post(()->{importing=false;chooseSegment(routes,id);});}catch(Exception e){error(e);}});}
    private void chooseSegment(List<GpsRoute> routes,String existing){if(isFinishing()||isDestroyed())return;
        if(routes.size()==1){if(existing!=null)setRoute(existing,routes.get(0));else store(routes.get(0));return;}
        String[] labels=new String[routes.size()];for(int i=0;i<labels.length;i++)labels[i]=routes.get(i).name+String.format(Locale.FRANCE," · %.2f km",routes.get(i).length/1000);
        new AlertDialog.Builder(this).setTitle("Choisir un segment continu (aucune liaison inventée)").setItems(labels,(d,i)->store(routes.get(i))).setNegativeButton("Annuler",null).show();
    }
    private void store(GpsRoute r){importing=true;io.execute(()->{try{String id=repository.save(Collections.singletonList(r));main.post(()->{importing=false;setRoute(id,r);});}catch(Exception e){error(e);}});}
    private void setRoute(String id,GpsRoute r){routeId=id;route=r;reversed=null;getPreferences(MODE_PRIVATE).edit().putString("route",id).apply();map.setRoute(reverse.isChecked()?previewReverse():route);title.setText(r.name);status.setText(String.format(Locale.FRANCE,"%.2f km · %d points · appui long pour choisir la reprise",r.length/1000,r.points.size()));}
    private void exportGpx(){if(routeId==null){toast("Choisir un parcours");return;}startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE,"vector_parcours.gpx"),102);}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;android.net.Uri uri=data.getData();
        importing=true;status.setText(request==101?"Import GPX…":"Export GPX…");final String id=routeId;
        io.execute(()->{try{
            if(request==101){List<GpsRoute> routes;try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Fichier illisible");routes=GpxIO.read(in);}main.post(()->{importing=false;chooseSegment(routes,null);});}
            else if(request==102){List<GpsRoute> routes=repository.read(id);try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Destination inaccessible");GpxIO.write(routes,out);}main.post(()->{importing=false;toast("GPX exporté");});}
        }catch(Exception e){error(e);}});
    }
    private void start(String action,boolean resume){
        if(action.equals(GpsNavigationService.START)&&GpsNavigationService.running){toast("Arrêter le suivi avant de recommencer ou reprendre");return;}
        if(action.equals(GpsNavigationService.START)&&routeId==null){toast("Choisir ou importer un parcours");return;}
        pendingAction=action;pendingResume=resume;ArrayList<String> needed=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){needed.add(Manifest.permission.ACCESS_FINE_LOCATION);needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!needed.isEmpty()){requestPermissions(needed.toArray(new String[0]),103);return;}startAllowed();
    }
    private void startAllowed(){if(pendingAction==null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){toast("Autoriser la localisation");return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){toast("Autoriser la notification pour le suivi écran éteint");return;}
        LocationManager l=(LocationManager)getSystemService(LOCATION_SERVICE);if(!l.isProviderEnabled(LocationManager.GPS_PROVIDER)&&!l.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){toast("Activer la localisation du téléphone");startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;}
        try{Intent i=new Intent(this,GpsNavigationService.class).setAction(pendingAction).putExtra("route",routeId).putExtra("reverse",reverse.isChecked()).putExtra("resume",pendingResume).putExtra("vibrate",vibrate.isChecked());startForegroundService(i);pendingAction=null;}catch(Exception e){error(e);}
    }
    private void command(String action){if(action.equals(GpsNavigationService.JOIN)&&GpsNavigationService.latest.route==null){toast("Démarrer un parcours avant de le rejoindre");return;}if(action.equals(GpsNavigationService.SAVE)&&!GpsNavigationService.latest.recording){toast("Aucune promenade en cours d’enregistrement");return;}if(!GpsNavigationService.running){toast("Démarrer d'abord le suivi ou l'enregistrement");return;}try{startService(new Intent(this,GpsNavigationService.class).setAction(action));}catch(Exception e){error(e);}}
    private void selectProgress(List<Double> candidates){if(!GpsNavigationService.running||GpsNavigationService.latest.route==null){toast("Démarrer le suivi pour choisir un point de reprise");return;}String[] labels=new String[candidates.size()];for(int i=0;i<labels.length;i++)labels[i]=String.format(Locale.FRANCE,"Reprendre au repère %.2f km",candidates.get(i)/1000);
        new AlertDialog.Builder(this).setTitle("Confirmer la position sur le parcours").setItems(labels,(d,i)->startService(new Intent(this,GpsNavigationService.class).setAction(GpsNavigationService.SELECT).putExtra("progress",candidates.get(i)))).setNegativeButton("Annuler",null).show();}
    private void recover(){if(!canChoose())return;File file=new File(getFilesDir(),"gps_recording.journal");if(!file.exists()){toast("Aucun enregistrement interrompu à récupérer");return;}
        new AlertDialog.Builder(this).setTitle("Enregistrement local interrompu").setItems(new String[]{"Récupérer les segments complets","Supprimer le journal"},(d,i)->{
            if(i==1){new AlertDialog.Builder(this).setMessage("Supprimer définitivement cet enregistrement non sauvegardé ?").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(a,b)->{if(!file.delete())toast("Suppression impossible");}).show();return;}
            importing=true;io.execute(()->{try{List<GpsRoute> r=GpsTrackJournal.recover(file);String id=repository.save(r);if(!file.delete())throw new IOException("GPX sauvé mais journal non supprimé");main.post(()->{importing=false;load(id);});}catch(Exception e){error(e);}});
        }).show();
    }
    private String describe(GpsNavigationService.State s){
        if(!s.running){if(!s.message.isEmpty())return s.message;return route==null?"Importer un GPX ou enregistrer une promenade":String.format(Locale.FRANCE,"%.2f km · prêt · Départ ou Reprendre",route.length/1000);}
        String label;
        switch(s.status){case TO_START:label="REJOINDRE LE DÉPART";break;case FOLLOWING:label=s.route==null?"ENREGISTREMENT GPS":"SUIVI DU PARCOURS";break;case OFF_ROUTE:label="HORS PARCOURS — rejoindre la trace";break;case AMBIGUOUS:label="BRANCHE AMBIGUË — appui long pour confirmer";break;case POOR_ACCURACY:label="GPS IMPRÉCIS — guidage suspendu";break;case STALE:label="POSITION PÉRIMÉE — guidage suspendu";break;case REACQUIRE:label="GPS RETROUVÉ — confirmer la reprise";break;case OUTLIER:label="SAUT GPS REJETÉ";break;case ARRIVED:label="ZONE D’ARRIVÉE ESTIMÉE";break;default:label="RECHERCHE GPS";}
        String detail=s.fix==null?"":Double.isFinite(s.fix.accuracy)?String.format(Locale.FRANCE,"GPS ±%.0f m",s.fix.accuracy):"Précision GPS inconnue";
        if(s.route!=null){detail+=String.format(Locale.FRANCE," · reste %.0f m",Math.max(0,s.route.length-s.progress));if(Double.isFinite(s.off))detail+=String.format(Locale.FRANCE," · écart %.0f m",s.off);}
        if(Double.isFinite(s.bearing)&&(s.status==GpsNavigator.Status.FOLLOWING||s.status==GpsNavigator.Status.OFF_ROUTE||s.status==GpsNavigator.Status.TO_START))detail+=String.format(Locale.FRANCE,"\nCible %.0f° nord vrai",s.bearing);
        return label+"\n"+detail+(s.recording?"\n● Enregistrement · "+s.recorded+" points":"")+(s.message.isEmpty()?"":"\n"+s.message);
    }
    private void help(){new AlertDialog.Builder(this).setTitle("Suivi GPS hors ligne").setMessage("Importer une trace GPX ou une route (lignes droites entre ses points, pas de calcul routier). Les segments discontinus restent séparés. Départ guide vers le premier point ; Reprendre utilise la progression sauvegardée. Rejoindre estime la position sur la trace, un appui long permet de la confirmer. Pincer pour zoomer, glisser pour déplacer, double appui pour ajuster.\n\nBleu : position GPS et cercle de précision annoncé. Jaune : estimation sur la trace lorsqu'elle est non ambiguë. Vert : départ ; rose : arrivée. Repères kilométriques le long du parcours. Le cap cible est relatif au nord vrai, pas à l'orientation du téléphone. Pas de fond de carte ni de réseau de chemins voisins ; la trace ne prouve pas que vous êtes sur le bon chemin parallèle.\n\nLe service GPS continue écran éteint avec notification ARRÊTER ; aucune caméra ni ARCore nécessaire. Il peut accompagner WALK. Précision insuffisante, interruption ou ambiguïté suspendent le guidage. Vibration d'écart optionnelle. Après fermeture forcée, redémarrer explicitement ; récupérer une promenade interrompue via Récupérer trace.\n\nLes positions restent sur ce téléphone, sans upload. L'export partage uniquement le GPX choisi. Arrivée et correspondance restent des estimations GPS.").setPositiveButton("OK",null).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(Exception e){main.post(()->{importing=false;if(!isFinishing()&&!isDestroyed()){status.setText("Erreur : "+e.getMessage());toast("Erreur : "+e.getMessage());}});}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==103)startAllowed();}
    @Override public void onResume(){super.onResume();main.post(refresh);}
    @Override public void onPause(){main.removeCallbacks(refresh);super.onPause();}
    @Override public void onDestroy(){io.shutdown();super.onDestroy();}
}
