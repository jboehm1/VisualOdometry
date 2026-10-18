package dev.jboehm.vectorvio.pdr.ai;

/**
 * Research-only, fully isolated neural candidate processor.
 * Defaults to NO MODEL and NEVER modifies PdrEngine distance, position or heading.
 * A verified model must be injected explicitly by a separate, reviewed loader.
 */
public final class NeuralDiagnosticRunner {
    private final InertialWindow buffer=new InertialWindow();
    private final NeuralMotionModel model;
    private long candidateCount, rejectedPredictions;
    private long lastInferenceNs=Long.MIN_VALUE;
    private NeuralMotionEstimate last;
    public NeuralDiagnosticRunner(NeuralMotionModel verifiedModelOrNull){
        model=verifiedModelOrNull;
    }
    public boolean hasModel(){return model!=null;}
    public long candidateCount(){return candidateCount;}
    public long rejectedPredictions(){return rejectedPredictions;}
    public NeuralMotionEstimate lastCandidate(){return last;}
    public InertialWindow buffer(){return buffer;}
    public void reset(){buffer.reset();lastInferenceNs=Long.MIN_VALUE;last=null;}
    public void gyro(long t,float x,float y,float z){buffer.gyro(t,x,y,z);}
    public NeuralMotionEstimate acceleration(long t,float x,float y,float z){
        if(!buffer.acceleration(t,x,y,z) || model==null)return null;
        // No predictions faster than once every 0.5 s; budget CPU for S21.
        long end=buffer.endTimeNs();
        if(lastInferenceNs!=Long.MIN_VALUE && end-lastInferenceNs<500_000_000L)
            return null;
        lastInferenceNs=end;
        try{
            NeuralMotionEstimate candidate=model.inferWindow(
                buffer.snapshot(),end,buffer.durationSeconds());
            candidateCount++;last=candidate;return candidate;
        }catch(IllegalArgumentException|IllegalStateException e){
            rejectedPredictions++;return null;
        }
    }
}
