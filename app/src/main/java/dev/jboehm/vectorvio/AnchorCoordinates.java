package dev.jboehm.vectorvio;

import com.google.ar.core.Pose;

/** Coordinate transport only: no smoothing, deadband or rotation classification.
 * Camera and anchor poses must come from the same ARCore frame. */
final class AnchorCoordinates {
    private Pose metricFromAnchor=Pose.IDENTITY;
    void reset(){metricFromAnchor=Pose.IDENTITY;}
    Pose camera(Pose worldFromAnchor,Pose worldFromCamera){
        return metricFromAnchor.compose(worldFromAnchor.inverse()).compose(worldFromCamera);
    }
    Pose worldFromMetric(Pose worldFromAnchor){return worldFromAnchor.compose(metricFromAnchor.inverse());}
    void rebase(Pose worldFromOld,Pose worldFromNew){
        // Same-frame transfer preserves both translation and orientation.
        metricFromAnchor=metricFromAnchor.compose(worldFromOld.inverse()).compose(worldFromNew);
    }
}
