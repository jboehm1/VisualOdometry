package dev.jboehm.vectorvio.nativevio;

/** Own on one acquisition thread. Native teardown can block: never close on the UI thread. */
public final class BasaltBridge implements AutoCloseable {
    private static final String unavailable;
    static {
        String error=null;
        try {System.loadLibrary("c++_shared");System.loadLibrary("opencv_java4");System.loadLibrary("vector_basalt");}
        catch(UnsatisfiedLinkError|SecurityException e){error=e.toString();}
        unavailable=error;
    }
    private long handle;
    public static String unavailableReason(){return unavailable;}
    public static String version(){return unavailable==null?nativeVersion():"UNAVAILABLE: "+unavailable;}
    public synchronized void start(double[] calibration,long firstTime,double[] firstPair){
        if(unavailable!=null)throw new IllegalStateException(unavailable);
        if(handle!=0)throw new IllegalStateException("Already started");
        handle=nativeStart(calibration,firstTime,firstPair);
        if(handle==0)throw new IllegalStateException("Native initialization failed");
    }
    public synchronized boolean running(){return handle!=0;}
    public synchronized boolean imu(long t,double[] pair){return handle!=0&&nativeImu(handle,t,pair);}
    public synchronized boolean frame(long t,byte[] gray,long exposure){return handle!=0&&nativeFrame(handle,t,gray,exposure);}
    /** xyz, quaternion xyzw, observed features, landmarks. Quality is a heuristic, not covariance. */
    public synchronized int poll(long[] timestamp,double[] pose){return handle==0?0:nativePoll(handle,timestamp,pose);}
    @Override public synchronized void close(){long old=handle;handle=0;if(old!=0)nativeStop(old);}
    private static native String nativeVersion();
    private static native long nativeStart(double[] p,long firstTime,double[] firstPair);
    private static native boolean nativeImu(long handle,long time,double[] values);
    private static native boolean nativeFrame(long handle,long time,byte[] pixels,long exposure);
    private static native int nativePoll(long handle,long[] timestamps,double[] values);
    private static native void nativeStop(long handle);
}
