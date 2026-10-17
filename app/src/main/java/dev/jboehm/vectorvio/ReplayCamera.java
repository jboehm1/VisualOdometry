package dev.jboehm.vectorvio;

/** Known sensor calibration, shared by video generation and the receiving estimator. */
final class ReplayCamera {
    static final int WIDTH=480,HEIGHT=360;
    static final float FOCAL=260f,CX=WIDTH*.5f,CY=HEIGHT*.5f;
    private ReplayCamera(){}
}
