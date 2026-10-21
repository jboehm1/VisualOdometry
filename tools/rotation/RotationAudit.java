package dev.jboehm.vectorvio;

/** Deterministic accumulator audit; NOT an ARCore simulation or phone test.
 * The common frame correction is constructed, not estimated from a recording. */
public final class RotationAudit {
    private static float[] yaw(double a) {
        return new float[]{0,(float)Math.sin(a/2),0,(float)Math.cos(a/2)};
    }
    public static void main(String[] args) {
        run("fixed center, changing orientation",false,false);
        run("fixed center, changing world frame",true,false);
        run("real 0.30m camera arc, changing world frame",true,true);
        Odometer noise=new Odometer();noise.start(0);
        for(int i=0;i<=1800;i++)noise.onPose(i%2==0?0:.001,0,0,1_000_000_000L+i*33_333_333L);
        System.out.printf(java.util.Locale.US,"constructed 1mm alternating pose error, 60s: %.6fm%n",noise.snapshot(0).totalMeters);
        if(Math.abs(noise.snapshot(0).totalMeters-1.8)>1e-8)throw new AssertionError("raw increments");
    }
    private static void run(String label,boolean changingFrame,boolean arc) {
        Odometer raw=new Odometer(),relative=new Odometer();raw.start(0);relative.start(0);
        for(int i=0;i<=360;i++) {
            double turn=i*Math.PI/180, g=changingFrame?turn/2:0;
            double physicalX=arc?.30*Math.cos(turn):1,physicalZ=arc?.30*Math.sin(turn):0;
            // World coordinates are G_k * physical coordinates, for camera AND anchor.
            double c=Math.cos(g),s=Math.sin(g),tx=changingFrame?.2*Math.sin(turn):0;
            double tz=changingFrame?.1*Math.cos(turn):0;
            double worldX=c*physicalX-s*physicalZ+tx,worldZ=s*physicalX+c*physicalZ+tz;
            // Inverse anchor transform at the SAME frame: R_a^T * (p_camera - p_anchor).
            double localX=c*(worldX-tx)+s*(worldZ-tz);
            double localZ=-s*(worldX-tx)+c*(worldZ-tz);
            long t=1_000_000_000L+i*33_333_333L;
            raw.onPose(worldX,0,worldZ,yaw(turn+g),t);
            relative.onPose(localX,0,localZ,yaw(turn),t);
        }
        double d=relative.snapshot(0).totalMeters,expected=arc?360*2*.30*Math.sin(Math.PI/360):0;
        System.out.printf(java.util.Locale.US,"%s: world-difference=%.6fm, ideal same-anchor-relative=%.6fm, expected=%.6fm%n",label,raw.snapshot(0).totalMeters,d,expected);
        if(Math.abs(d-expected)>1e-9)throw new AssertionError(label);
    }
}
