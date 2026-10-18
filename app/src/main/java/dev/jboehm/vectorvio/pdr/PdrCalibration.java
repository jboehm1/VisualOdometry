package dev.jboehm.vectorvio.pdr;

/**
 * Manual, independently measured distance / counted-step calibration.
 * No truth from the held-out benchmark dataset is ever used to infer stride.
 * These are guard rails against input mistakes, not physiological claims.
 */
public final class PdrCalibration {
    private PdrCalibration() {}
    public static double calibratedStepLength(double measuredDistanceM,int observedSteps) {
        if (!Double.isFinite(measuredDistanceM) || measuredDistanceM<2.0
                || measuredDistanceM>50_000.0)
            throw new IllegalArgumentException("reference distance must be 2–50000 m");
        if (observedSteps<5)
            throw new IllegalArgumentException("at least five detected steps required");
        double stride=measuredDistanceM/observedSteps;
        if (stride<.35 || stride>1.8)
            throw new IllegalArgumentException("derived stride outside 0.35–1.8 m; check reference and steps");
        return stride;
    }
}
