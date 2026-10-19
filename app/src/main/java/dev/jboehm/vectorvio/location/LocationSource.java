package dev.jboehm.vectorvio.location;

/** Provider boundary: the estimator has no Android / Google dependency. Foreground only. */
public interface LocationSource extends AutoCloseable {
    enum Mode { AUTO, GPS, FUSED }
    interface Listener {
        void onSample(LocationSample sample);
        void onStatus(String status);
    }
    void start(Mode mode);
    void stop();
    @Override void close();
}
