package dev.jboehm.vectorvio;

/**
 * Scene-driven Camera2 manual AE for fastest reasonable shutter with a hard ISO
 * ceiling. The measured luma is *ISP gamma encoded* preview brightness, not lux.
 *
 * Experimental 30 fps Camera2-only lab. Never overrides an ARCore capture
 * request; for ARCore, collect quality telemetry and use supported FPS profiles.
 */
public final class CameraExposureOptimizer {
    public static final class Limits {
        public final long minExposureNs,maxExposureNs;
        public final int minIso,maxIso;
        public Limits(long minExposureNs,long maxExposureNs,int minIso,int maxIso){
            if(minExposureNs<=0||maxExposureNs<minExposureNs||minIso<1||maxIso<minIso)
                throw new IllegalArgumentException("Invalid hardware ranges");
            this.minExposureNs=minExposureNs;this.maxExposureNs=maxExposureNs;
            this.minIso=minIso;this.maxIso=maxIso;
        }
    }
    public static final class Decision {
        public final long exposureNs;
        public final int iso;
        public final boolean insufficientLight,clipped,missingTexture,changed;
        public final String status;
        Decision(long exposure,int iso,boolean dark,boolean clipped,boolean texture,
                 boolean changed,String status){
            this.exposureNs=exposure;this.iso=iso;insufficientLight=dark;
            this.clipped=clipped;missingTexture=texture;this.changed=changed;this.status=status;
        }
    }
    private static final long MIN_UPDATE_NS=650_000_000L;
    private long lastAdjustmentNs=Long.MIN_VALUE;
    private double product=Double.NaN;
    private static long clamp(long value,long min,long max){return Math.max(min,Math.min(max,value));}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    public void reset(){lastAdjustmentNs=Long.MIN_VALUE;product=Double.NaN;}

    /**
     * nsec shutter, ISO and target luma 55..150. A 1.5 gamma correction factor
     * gives a bounded approximate exposure-step; negative feedback, never a
     * calibrated sensor response model. Wait 650ms for camera settling.
     */
    public Decision update(long nowNs,CameraImageQuality.Metrics image,
        long actualExposureNs,int actualIso,Limits limits,int maxAcceptableIso,int targetLuma){
        if(image==null||image.pixels<64||limits==null||nowNs<0)
            throw new IllegalArgumentException("Missing evidence");
        int isoCeiling=clamp(maxAcceptableIso,limits.minIso,limits.maxIso);
        int goal=clamp(targetLuma,55,150);
        long exposure=clamp(actualExposureNs,limits.minExposureNs,limits.maxExposureNs);
        int iso=clamp(actualIso,limits.minIso,isoCeiling);
        if(!Double.isFinite(product)){
            product=(double)exposure*iso;
        }
        if(lastAdjustmentNs!=Long.MIN_VALUE&&nowNs-lastAdjustmentNs<MIN_UPDATE_NS)
            return new Decision(exposure,iso,image.dark(),image.clippedFraction>.08,
                image.lacksTexture(),false,"SETTLING");

        boolean saturated=image.clippedFraction>.08;
        double value=Math.max(3,image.p50);
        double errorEv=1.5*Math.log(goal/value)/Math.log(2);
        if(saturated)errorEv=Math.min(errorEv,-.35);
        if(Math.abs(errorEv)<.14)errorEv=0;
        // Change <0.5 EV per feedback cycle to prevent high-gain oscillations.
        errorEv=Math.max(-.43,Math.min(.43,errorEv));
        product=Math.max((double)limits.minExposureNs*limits.minIso,
            Math.min((double)limits.maxExposureNs*isoCeiling,product*Math.pow(2,errorEv)));
        // FAST SHUTTER PRIORITY: push ISO up to the allowed (user-selected)
        // noise ceiling first, only then lengthen exposure if scene darkens.
        long shutter=clamp(Math.round(product/isoCeiling),
                          limits.minExposureNs,limits.maxExposureNs);
        int neededIso=clamp((int)Math.round(product/shutter),
                           limits.minIso,isoCeiling);
        // If current AE estimate is at goal, avoid spamming setRepeatingRequest.
        boolean changed=(Math.abs(Math.log((double)shutter/exposure))>.09 ||
            Math.abs(neededIso-iso)>Math.max(15,iso*.09));
        lastAdjustmentNs=nowNs;
        boolean underexposed=image.p50<goal*.65 && shutter>=limits.maxExposureNs*.97
            &&neededIso>=isoCeiling*.97;
        return new Decision(shutter,neededIso,underexposed||image.dark(),
            saturated,image.lacksTexture(),changed,
            underexposed?"LIGHT LIMIT · need illumination or slower FPS":
            image.lacksTexture()?"LOW TEXTURE · tracking may fail":
            saturated?"HIGHLIGHTS CLIPPED":image.dark()?"DIM · adapting":"NORMAL");
    }
}
