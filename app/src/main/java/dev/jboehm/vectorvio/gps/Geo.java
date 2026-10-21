package dev.jboehm.vectorvio.gps;
/** WGS84 coordinates; spherical great-circle distances (mean Earth radius).
 * Not a survey-grade ellipsoidal geodesic. Handles antimeridian and poles. */
public final class Geo {
    public static final double R=6371008.8;
    private Geo(){}
    public static boolean valid(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    public static double distance(double la,double lo,double lb,double ln){
        double a=Math.toRadians(la),b=Math.toRadians(lb),d=b-a,l=Math.toRadians(ln-lo);
        double h=Math.sin(d/2)*Math.sin(d/2)+Math.cos(a)*Math.cos(b)*Math.sin(l/2)*Math.sin(l/2);
        return 2*R*Math.atan2(Math.sqrt(Math.max(0,h)),Math.sqrt(Math.max(0,1-h)));
    }
    public static double bearing(double la,double lo,double lb,double ln){
        double a=Math.toRadians(la),b=Math.toRadians(lb),l=Math.toRadians(ln-lo);
        return (Math.toDegrees(Math.atan2(Math.sin(l)*Math.cos(b),Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(l)))+360)%360;
    }
    static double[] unit(double lat,double lon){double a=Math.toRadians(lat),b=Math.toRadians(lon);return new double[]{Math.cos(a)*Math.cos(b),Math.cos(a)*Math.sin(b),Math.sin(a)};}
    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double norm(double[] p){return Math.sqrt(dot(p,p));}
}
