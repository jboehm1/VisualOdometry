package dev.jboehm.vectorvio.pdr.research;

/** Causal research challenger. Not connected to Android or PdrEngine. */
public final class PeakCandidate {
    private final boolean adaptive;
    private boolean initialized, rising;
    private double gravity, filtered, previous, variance;
    private long last = -1, first = -1, candidate = -1;

    public PeakCandidate(boolean adaptive) { this.adaptive = adaptive; }

    public void reset() {
        initialized = rising = false;
        gravity = filtered = previous = variance = 0;
        last = first = candidate = -1;
    }

    public boolean accept(long time, double x, double y, double z) {
        if (time <= 0 || time <= last || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        double magnitude = Math.hypot(Math.hypot(x, y), z);
        if (!Double.isFinite(magnitude) || magnitude > 80) return false;
        if (!initialized || time - last > 1_500_000_000L) {
            reset(); initialized = true;
            gravity = magnitude; last = first = time;
            return false;
        }
        long dt = time - last; last = time;
        gravity += (1-Math.exp(-dt/300_000_000.0))*(magnitude-gravity);
        double high = magnitude-gravity;
        filtered += (1-Math.exp(-dt/20_000_000.0))*(high-filtered);
        variance += (1-Math.exp(-dt/2_000_000_000.0))*(high*high-variance);
        double threshold = adaptive ? Math.max(.8, Math.min(2., .65*Math.sqrt(variance))) : 1.15;
        boolean peak = rising && filtered < previous && previous > threshold;
        rising = filtered > previous;
        previous = filtered;
        if (!peak || time-first < 200_000_000L) return false;
        if (candidate < 0) { candidate = time; return false; }
        long period = time-candidate;
        if (period < 300_000_000L) return false;
        candidate = time;
        // Require two physically plausible peaks, but no inferred fixed rhythm:
        // changes of gait and asymmetric hand motion need not have equal periods.
        return period <= 1_250_000_000L;
    }
}
