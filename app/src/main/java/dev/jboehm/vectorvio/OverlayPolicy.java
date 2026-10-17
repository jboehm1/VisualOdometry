package dev.jboehm.vectorvio;
/** Circuit breaker for display only. Failure never changes odometry/recording state. */
final class OverlayPolicy {
    private boolean canvas,failed;
    OverlayPolicy(boolean canvas,boolean failed){this.canvas=canvas;this.failed=failed;}
    boolean useGpu(){return !canvas&&!failed;}
    boolean failed(){return failed;}
    boolean canvas(){return canvas;}
    void failure(){failed=true;}
    void chooseCanvas(){canvas=true;}
    void chooseAuto(){canvas=false;}
    void retry(){canvas=false;failed=false;}
}
