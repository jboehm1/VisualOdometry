package dev.jboehm.vectorvio.pdr.ai;

/**
 * An UNFUSED, device-coordinate neural candidate. Not a GNSS position,
 * world-frame displacement, body heading, path, or calibrated covariance.
 *
 * Only models with independently reviewed weight provenance should ever
 * produce these estimates outside diagnostic/development screens.
 */
public final class NeuralMotionEstimate {
    public final long endTimeNs;
    public final double deviceXMetres, deviceYMetres;
    public final double modelScaleX, modelScaleY;
    public final String modelId;
    public final String outputFrame;
    public final boolean covarianceCalibrated;

    NeuralMotionEstimate(long end, double x, double y, double sx, double sy,
                         String id, String frame) {
        endTimeNs=end; deviceXMetres=x; deviceYMetres=y;
        modelScaleX=sx; modelScaleY=sy; modelId=id; outputFrame=frame;
        // A positive scale from a model is NOT empirical uncertainty calibration.
        covarianceCalibrated=false;
    }
}
