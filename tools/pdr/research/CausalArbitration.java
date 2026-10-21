package dev.jboehm.vectorvio.pdr.research;
import dev.jboehm.vectorvio.pdr.StepPeakDetector;
/** Research adapter; defaults retain the initial 1.15 experiment. */
public final class CausalArbitration {
    private final StepPeakDetector.Arbitration delegate;
    public CausalArbitration() { this(1.15); }
    public CausalArbitration(double ratio) { delegate=new StepPeakDetector.Arbitration(ratio); }
    public void reset() { delegate.reset(); }
    public int switches() { return delegate.switches(); }
    public boolean accept(long t,boolean base,boolean candidate) { return delegate.accept(t,base,candidate); }
}
