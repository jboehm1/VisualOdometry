package dev.jboehm.vectorvio;

/**
 * EXPERIMENTAL Camera2 manual AE in a separate optical lab, never ARCore.
 *
 * Scene median feedback estimates required exposure * ISO. The controller
 * allocates that budget with a SHORT-SHUTTER preference up to the user's
 * independently selected ISO noise ceiling, plus a motion-derived shutter cap.
 * The image histogram is gamma-encoded preview data, NOT scene lux or SNR.
 */
public final class CameraExposureOptimizer {
    public enum MotionMode { WALK, BIKE }
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
        public final long exposureNs,shutterCeilingNs;
        public final int iso;
        public final double predictedAngularBlurDeg;
        public final boolean insufficientLight,clipped,missingTexture,changed,motionConstrained;
        public final String status;
        Decision(long exposure,int iso,boolean dark,boolean clipped,boolean texture,
                 boolean changed,String status,long cap,double blurDeg,boolean motionConstrained){
            this.exposureNs=exposure;this.iso=iso;insufficientLight=dark;
            this.clipped=clipped;missingTexture=texture;this.changed=changed;this.status=status;
            this.shutterCeilingNs=cap;this.predictedAngularBlurDeg=blurDeg;
            this.motionConstrained=motionConstrained;
        }
    }
    private static final long MIN_UPDATE_NS=650_000_000L;
    private long lastAdjustmentNs=Long.MIN_VALUE;
    private double exposureBudget=Double.NaN;
    private static long clamp(long value,long min,long max){return Math.max(min,Math.min(max,value));}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}

    public void reset(){lastAdjustmentNs=Long.MIN_VALUE;exposureBudget=Double.NaN;}

    /** Pure geometric angular-smear budget, never an estimate of translation blur. */
    public static long motionShutterCapNs(double gyroRadPerSecond,MotionMode mode,Limits limits){
        if(limits==null)throw new IllegalArgumentException("No hardware bounds");
        MotionMode selected=mode==null?MotionMode.WALK:mode;
        // 1/100s for bike, 1/40s walking; both are provisional lab defaults.
        long base=selected==MotionMode.BIKE?10_000_000L:25_000_000L;
        double angleLimitDeg=selected==MotionMode.BIKE?.18:.30;
        long motionCap=base;
        if(Double.isFinite(gyroRadPerSecond)&&gyroRadPerSecond>=.05){
            double seconds=Math.toRadians(angleLimitDeg)/gyroRadPerSecond;
            motionCap=Math.max(1,Math.round(seconds*1e9));
        }
        return clamp(Math.min(base,motionCap),limits.minExposureNs,limits.maxExposureNs);
    }

    /** Legacy call keeps existing callers/tests, but does not assert motion knowledge. */
    public Decision update(long nowNs,CameraImageQuality.Metrics image,
        long actualExposureNs,int actualIso,Limits limits,int maxAcceptableIso,int targetLuma){
        return updateMotionAware(nowNs,image,actualExposureNs,actualIso,limits,
            maxAcceptableIso,targetLuma,Double.NaN,MotionMode.WALK);
    }

    /**
     * At most a 0.43EV negative-feedback step per settled camera result.
     * ISO is NOT photons: this trades noise for reduced smear until ISO cap.
     *
     * If exposure budget exceeds max permitted shutter * ISO, report
     * underexposure instead of silently violating the blur/ISO limits.
     */
    public Decision updateMotionAware(long nowNs,CameraImageQuality.Metrics image,
        long actualExposureNs,int actualIso,Limits limits,int maxAcceptableIso,
        int targetLuma,double gyroRadPerSecond,MotionMode mode){
        if(image==null||image.pixels<64||limits==null||nowNs<0)
            throw new IllegalArgumentException("Missing evidence");
        int isoCeiling=clamp(maxAcceptableIso,limits.minIso,limits.maxIso);
        int goal=clamp(targetLuma,55,150);
        long actualExposure=clamp(actualExposureNs,limits.minExposureNs,limits.maxExposureNs);
        int actualGain=clamp(actualIso,limits.minIso,limits.maxIso);
        long shutterLimit=motionShutterCapNs(gyroRadPerSecond,mode,limits);
        double speed=Double.isFinite(gyroRadPerSecond)&&gyroRadPerSecond>0?
            gyroRadPerSecond:Double.NaN;
        if(!Double.isFinite(exposureBudget))exposureBudget=(double)actualExposure*actualGain;
        double budgetMax=(double)shutterLimit*isoCeiling;
        double budgetMin=(double)limits.minExposureNs*limits.minIso;
        exposureBudget=Math.max(budgetMin,Math.min(budgetMax,exposureBudget));
        if(lastAdjustmentNs!=Long.MIN_VALUE&&nowNs-lastAdjustmentNs<MIN_UPDATE_NS){
            // Blur is irreversible. If motion suddenly increases, don't wait for
            // the next 650ms brightness cycle before cutting the shutter.
            if(actualExposure>shutterLimit*1.15){
                int maxIsoNow=clamp((int)Math.round(exposureBudget/shutterLimit),
                    limits.minIso,isoCeiling);
                lastAdjustmentNs=nowNs;
                return new Decision(shutterLimit,maxIsoNow,image.dark(),
                    image.clippedFraction>.08,image.lacksTexture(),true,
                    "MOTION PRIORITY · EXPOSURE CUT",
                    shutterLimit,Double.isFinite(speed)?
                        Math.toDegrees(speed*shutterLimit/1e9):Double.NaN,true);
            }
            return new Decision(actualExposure,actualGain,image.dark(),
                image.clippedFraction>.08,image.lacksTexture(),false,"SETTLING",
                shutterLimit,Double.isFinite(speed)?
                    Math.toDegrees(speed*actualExposure/1e9):Double.NaN,
                actualExposure>shutterLimit);
        }

        // Textured, coherent-gradient regions are more relevant to potential
        // feature tracking than e.g. featureless sky. Strong spatial smoothing
        // suppresses some single-pixel sensor noise; NOT a certified SLAM score.
        boolean validFeatures=image.coherentEdgeFraction>=.045;
        double measured=validFeatures?
            .65*image.featureMedian+.35*image.p50:image.p50;
        double value=Math.max(3,measured);
        // Preview is gamma/tone mapped. This is only a bounded feedback heuristic.
        double errorEv=1.5*Math.log(goal/value)/Math.log(2);
        boolean saturated=image.clippedFraction>.08;
        if(saturated){
            // Preserve shadow detail in textured parts of high-contrast scenes
            // without blindly blowing out sky/highlights.
            errorEv=validFeatures&&image.featureMedian<goal*.75?
                Math.min(errorEv,.12):Math.min(errorEv,-.35);
        }
        if(Math.abs(errorEv)<.14)errorEv=0;
        errorEv=Math.max(-.43,Math.min(.43,errorEv));
        exposureBudget=Math.max(budgetMin,Math.min(budgetMax,
            exposureBudget*Math.pow(2,errorEv)));
        long shutter=clamp(Math.round(exposureBudget/isoCeiling),
            limits.minExposureNs,shutterLimit);
        int iso=clamp((int)Math.round(exposureBudget/shutter),
            limits.minIso,isoCeiling);
        boolean changed=Math.abs(Math.log((double)shutter/actualExposure))>.09 ||
            Math.abs(iso-actualGain)>Math.max(15,actualGain*.09);
        lastAdjustmentNs=nowNs;

        // Low light is evaluated in the observed image AFTER the controller is
        // at both configured limits. Do not mistake a dim sky for untracked VO.
        boolean atHardwareOrMotionLimit=shutter>=shutterLimit*.97 && iso>=isoCeiling*.97;
        boolean underexposed=value<goal*.65 && atHardwareOrMotionLimit;
        double smear=Double.isFinite(speed)?Math.toDegrees(speed*shutter/1e9):Double.NaN;
        String status=underexposed?"LIGHT / MOTION LIMIT":
            saturated?"HIGHLIGHTS CLIPPED":
            image.lacksTexture()?"LOW TEXTURE":
            image.dark()?"DIM · ADAPTING":"NORMAL";
        return new Decision(shutter,iso,underexposed||image.dark(),saturated,
            image.lacksTexture(),changed,status,shutterLimit,smear,
            atHardwareOrMotionLimit);
    }
}
