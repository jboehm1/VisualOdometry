package dev.jboehm.vectorvio.geo;
/** Ellipsoidal WGS84 surface -> local East/North tangent projection, and inverse.
 * Reference height is zero on the ellipsoid for horizontal geodesy only;
 * it does NOT provide terrain height or convert GPX elevation datums. */
public final class Wgs84LocalFrame {
    private static final double A=6378137.0,F=1/298.257223563,B=A*(1-F),E2=F*(2-F);
    private final double[] origin,east,north,up;
    public Wgs84LocalFrame(double lat,double lon){
        check(lat,lon);double p=Math.toRadians(lat),l=Math.toRadians(lon);
        origin=ecef(lat,lon);east=new double[]{-Math.sin(l),Math.cos(l),0};
        north=new double[]{-Math.sin(p)*Math.cos(l),-Math.sin(p)*Math.sin(l),Math.cos(p)};
        up=new double[]{Math.cos(p)*Math.cos(l),Math.cos(p)*Math.sin(l),Math.sin(p)};
    }
    private static void check(double lat,double lon){if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("Invalid WGS84 position");}
    private static double[] ecef(double lat,double lon){
        double p=Math.toRadians(lat),l=Math.toRadians(lon),n=A/Math.sqrt(1-E2*Math.sin(p)*Math.sin(p));
        return new double[]{n*Math.cos(p)*Math.cos(l),n*Math.cos(p)*Math.sin(l),n*(1-E2)*Math.sin(p)};
    }
    public double[] toLocal(double lat,double lon){
        check(lat,lon);double[] q=ecef(lat,lon);double e=0,n=0;
        for(int i=0;i<3;i++){double d=q[i]-origin[i];e+=d*east[i];n+=d*north[i];}return new double[]{e,n};
    }
    public double[] toGeographic(double e,double n){
        if(!Double.isFinite(e)||!Double.isFinite(n))return null;
        double[] p=new double[3];for(int i=0;i<3;i++)p[i]=origin[i]+e*east[i]+n*north[i];
        double aa=0,bb=0,cc=-1;
        for(int i=0;i<3;i++){double r=i==2?B:A;aa+=up[i]*up[i]/(r*r);bb+=2*p[i]*up[i]/(r*r);cc+=p[i]*p[i]/(r*r);}
        double disc=bb*bb-4*aa*cc;if(disc<0)return null;
        double u=-2*cc/(bb+Math.sqrt(disc));for(int i=0;i<3;i++)p[i]+=u*up[i];
        return new double[]{Math.toDegrees(Math.atan2(p[2]*A*A,Math.hypot(p[0],p[1])*B*B)),Math.toDegrees(Math.atan2(p[1],p[0]))};
    }
}
