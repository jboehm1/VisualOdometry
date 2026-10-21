package dev.jboehm.vectorvio.pdr.ai;

/**
 * Backend-agnostic neural inference contract.
 * RoNIN/TCN, TLIO, EqNIO and mobile ONNX/TFLite adapters must define their
 * OWN preprocessing, coordinate frame and model provenance; no pretrained
 * checkpoint can be silently treated as compatible with the compact MLP.
 *
 * This interface emits UNFUSED DIAGNOSTIC CANDIDATES only. Never feed
 * predictions into PdrEngine without separately audited fusion gates.
 */
public interface NeuralMotionModel {
    String modelId();
    String outputFrame();
    NeuralMotionEstimate inferWindow(float[] timeMajorDeviceImu64x6,
                                     long windowEndNs,double windowSeconds);
}
