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
            // Do not combine setMessage + setItems: the Android alert
            // layout may hide the choice list when a message is supplied.
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
            .setNeutralButton("Limites", (dialog,which)->
                new AlertDialog.Builder(host)
                    .setTitle("Limites de chaque moteur")
                    .setMessage("Même HUD d'odométrie pour ARCore et Basalt, mais leurs positions et échelles ne sont jamais fusionnées. "
                        +"ARCore seul permet le GPX 3D, les cartes et points AR. Basalt contrôle ISO/shutter avec Camera2, "
                        +"nécessite une calibration usine expérimentale et n'est pas validé sur S21. "
                        +"Le mode PDR fonctionne sans caméra dans une vue dédiée. "
                        +"Le changement de moteur relâche la caméra et recommence le repère local.")
                    .setPositiveButton("OK",null).show())
            .setNegativeButton("Annuler",null).show();
    }
}
