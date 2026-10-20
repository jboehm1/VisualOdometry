package dev.jboehm.vectorvio;

import java.util.Locale;

/** Native location display only. Never injected into ARCore odometry or the GPX alignment. */
final class GpsSpeedStatus {
    static final long MAX_AGE_MS=5_000;
    static final class Reading {
        final String speedLabel,detail;
        final boolean warning;
        Reading(String speedLabel,String detail,boolean warning){
            this.speedLabel=speedLabel;this.detail=detail;this.warning=warning;
        }
    }
    static Reading unavailable(String reason){
        return new Reading("— km/h",reason,true);
    }
    static Reading evaluate(long ageMs,boolean nativeGnss,float accuracyMeters,
                            boolean hasSpeed,float speedMps,
                            boolean hasSpeedAccuracy,float speedAccuracyMps){
        if(ageMs<0||ageMs>MAX_AGE_MS)return unavailable("GPS STALE");
        if(!Float.isFinite(accuracyMeters)||accuracyMeters<0)return unavailable("GPS NO ACCURACY");
        String provider=nativeGnss?"GPS":"NETWORK";
        String accuracy=String.format(Locale.US,"%s ±%.0f m",provider,accuracyMeters);
        if(!hasSpeed||!Float.isFinite(speedMps)||speedMps<0)
            return new Reading("— km/h",accuracy+" · NO SPEED",true);
        String speed=String.format(Locale.US,"%.1f km/h",speedMps*3.6f);
        String speedError=hasSpeedAccuracy&&Float.isFinite(speedAccuracyMps)&&speedAccuracyMps>=0
            ?String.format(Locale.US," · v±%.1f",speedAccuracyMps*3.6f):" · v±?";
        // Android horizontal accuracy is not a radio signal strength or a confidence guarantee.
        return new Reading(speed,accuracy+speedError,!nativeGnss||accuracyMeters>35f);
    }
    private GpsSpeedStatus(){}
}
