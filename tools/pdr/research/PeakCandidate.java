package dev.jboehm.vectorvio.pdr.research;
import dev.jboehm.vectorvio.pdr.StepPeakDetector;
/** Research adapter around the exact Android-free production arithmetic. */
public final class PeakCandidate {
    private final StepPeakDetector.AdaptivePeaks delegate;
    public PeakCandidate(boolean adaptive) { delegate=new StepPeakDetector.AdaptivePeaks(adaptive); }
    public void reset() { delegate.reset(); }
    public long eventTimestampNs() { return delegate.eventTimestampNs(); }
    public boolean accept(long t,double x,double y,double z) { return delegate.accept(t,x,y,z); }
}
