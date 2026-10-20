package dev.jboehm.vectorvio;

/**
 * Experimentally reproduces FOUR ideas explicitly described in Sun et al.
 * 2025 abstract: local gradient weighting, quality derivative w.r.t exposure,
 * irradiance-dependent step, and motion-limited shutter + gain.
 *
 * THIS IS NOT THEIR EXACT NUMERIC IMPLEMENTATION. Full paper formulas/code
 * were unavailable for verification. Our derivative uses a gamma-projected
 * virtual ±0.25EV image pair; motion feedback includes local optical flow
 * proxy from separate Camera2 preview, not actual ORB-SLAM/VINS tracking.
 */
public final class RegionGradientExposureController {
    public static final class Decision {
        public final long exposureNs,shutterCapNs;
        public final int iso;
        public final String status;
        public final boolean changed,underexposed;
        public final double gradientScore,gradientSlope,stepEv;
        Decision(long shutter,int iso,long cap,String status,boolean change,
                 boolean dark,double score,double slope,double step){
            exposureNs=shutter;this.iso=iso;shutterCapNs=cap;this.status=status;
            changed=change;underexposed=dark;gradientScore=score;
            gradientSlope=slope;stepEv=step;
        }
    }
    private double budget=Double.NaN;
    private long lastUpdateNs=Long.MIN_VALUE;
    private static final long SETTLE_NS=650_000_000L;
    public void reset(){budget=Double.NaN;lastUpdateNs=Long.MIN_VALUE;}
    private static long clamp(long v,long low,long high){return Math.max(low,Math.min(high,v));}
    private static int clamp(int v,int low,int high){return Math.max(low,Math.min(high,v));}
    private static double clamp(double v,double low,double high){return Math.max(low,Math.min(high,v));}

    public Decision update(long nowNs,RegionWeightedGradient.Result metric,
       CameraImageQuality.Metrics image,long measuredShutterNs,int measuredIso,
       CameraExposureOptimizer.Limits hardware,int requestedIsoCeiling,
       double gyroRadPerSecond,CameraExposureOptimizer.MotionMode mode,
       CameraPreviewFlow.Result flow){
        return update(nowNs,metric,image,measuredShutterNs,measuredIso,hardware,
            requestedIsoCeiling,gyroRadPerSecond,mode,flow,90);
    }

    public Decision update(long nowNs,RegionWeightedGradient.Result metric,
       CameraImageQuality.Metrics image,long measuredShutterNs,int measuredIso,
       CameraExposureOptimizer.Limits hardware,int requestedIsoCeiling,
       double gyroRadPerSecond,CameraExposureOptimizer.MotionMode mode,
       CameraPreviewFlow.Result flow,int fallbackTargetLuma){
        if(hardware==null||metric==null||image==null||nowNs<0)
            throw new IllegalArgumentException("No capture feedback");
        int isoCap=clamp(requestedIsoCeiling,hardware.minIso,hardware.maxIso);
        long gyroCap=CameraExposureOptimizer.motionShutterCapNs(
            gyroRadPerSecond,mode,hardware);
        long opticCap=CameraPreviewFlow.blurCapNs(flow,hardware.minExposureNs,hardware.maxExposureNs);
        long cap=Math.max(hardware.minExposureNs,Math.min(gyroCap,opticCap));
        long observed=clamp(measuredShutterNs,hardware.minExposureNs,hardware.maxExposureNs);
        int gain=clamp(measuredIso,hardware.minIso,hardware.maxIso);
        double lowBudget=(double)hardware.minExposureNs*hardware.minIso;
        double maxBudget=(double)cap*isoCap;
        if(!Double.isFinite(budget))budget=(double)observed*gain;
        budget=clamp(budget,lowBudget,maxBudget);

        // Irreversible blur is urgent even if 650ms feedback settling is active.
        boolean urgent=observed>cap*1.15;
        boolean settled=lastUpdateNs==Long.MIN_VALUE||nowNs-lastUpdateNs>=SETTLE_NS;
        double step=0;
        String cause=metric.reliable?"GRADIENT_OPTIMUM":"BRIGHTNESS_FALLBACK";
        if(settled){
            if(metric.reliable){
                // Derivative dQ / d(EV), normalized to dimensionless local
                // logarithmic gradient for phone-to-phone score transfer.
                double normalized=metric.derivativePerEv/Math.max(4,metric.quality);
                double irradianceGain=metric.sceneIrradianceProxy<.04?1.30:
                     metric.sceneIrradianceProxy>.50?.78:1;
                step=clamp(.65*normalized*irradianceGain,-.38,.38);
                // Don't optimize noise-like numerical changes.
                if(Math.abs(step)<.055)step=0;
            }else{
                // Safety: meaningful low texture / overexposure should still be
                // bounded like baseline AE, not guess a derivative from noise.
                double error=Math.log(clamp(fallbackTargetLuma,55,150)/(double)Math.max(5,image.p50))/Math.log(2);
                step=clamp(error*.22,-.22,.22);
                if(Math.abs(step)<.07)step=0;
            }
            if(metric.clippedFraction>.20)step=Math.min(step,-.08);
            budget=clamp(budget*Math.pow(2,step),lowBudget,maxBudget);
            lastUpdateNs=nowNs;
        }
        // Use ISO up to user ceiling to get the shortest shutter for the
        // desired signal, but never exceed a hard blur bound.
        long targetShutter=clamp(Math.round(budget/isoCap),
            hardware.minExposureNs,cap);
        int targetIso=clamp((int)Math.round(budget/targetShutter),
            hardware.minIso,isoCap);
        boolean changed=urgent ||
            (settled&&(Math.abs(Math.log((double)targetShutter/observed))>.09||
            Math.abs(targetIso-gain)>Math.max(16,gain*.09)));
        boolean dark=image.p50<48&&targetShutter>=cap*.96&&
            targetIso>=isoCap*.96;
        String status=dark?"LIGHT / BLUR CAP (NO RELIABLE FEATURES)":
            urgent?"FAST MOTION: SHUTTER CUT":
            !settled?"SETTLING":cause;
        return new Decision(targetShutter,targetIso,cap,status,changed,
            dark,metric.quality,metric.derivativePerEv,step);
    }
}
