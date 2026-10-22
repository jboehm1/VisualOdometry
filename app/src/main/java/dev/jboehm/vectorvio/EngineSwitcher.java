package dev.jboehm.vectorvio;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * Same estimator selector for ARCore and the Camera2 + Basalt full-screen HUD.
 * Selection is explicit and no active recorder is silently disposed.
 * A shared UI is not evidence the algorithms share positions, calibration,
 * tracking anchors, GPX projection or live camera ownership.
 */
public final class EngineSwitcher {
    public enum Engine {
        ARCORE("ARCore · référence",true),
        BASALT("Basalt · VIO custom expérimental",false),
        PDR("PDR · sans caméra",false);
        public final String label;
        public final boolean gpxArSupported;
        Engine(String label,boolean gpx){this.label=label;gpxArSupported=gpx;}
    }
    public interface Selection {void choose(Engine target);}
    private EngineSwitcher(){}
    public static boolean canSwitch(Engine current,Engine target,boolean activeRecording){
        return current!=null&&target!=null&&target!=current&&!activeRecording;
    }
    public static void show(Activity host,Engine current,boolean recording,Selection onSelect){
        Engine[] values=Engine.values();
        String[] choices=new String[values.length];
        for(int i=0;i<values.length;i++)
            choices[i]=(values[i]==current?"● ":"○ ")+values[i].label;
        new AlertDialog.Builder(host)
            .setTitle("VECTOR VIO · moteur de suivi")
            .setMessage("Même compteur / HUD pour ARCore et Basalt. Le changement redémarre la caméra et réinitialise le repère. "
                +"ARCore seul prend en charge le GPX 3D, les cartes/points AR et leur calage. "
                +"Basalt produit des poses expérimentales indépendantes avec réglages Camera2. "
                +"PDR conserve son écran sans caméra. Aucun basculement ne fusionne automatiquement les trajectoires.")
            .setItems(choices,(dialog,index)->{
                Engine next=values[index];
                if(next==current)return;
                if(recording){
                    new AlertDialog.Builder(host)
                        .setTitle("Enregistrement actif")
                        .setMessage("Arrêtez et exportez la session avant de changer de moteur. "
                            +"La position et la distance ne sont pas continues entre deux moteurs.")
                        .setPositiveButton("Compris",null).show();
                    return;
                }
                onSelect.choose(next);
            })
            .setNegativeButton("Annuler",null).show();
    }
}
