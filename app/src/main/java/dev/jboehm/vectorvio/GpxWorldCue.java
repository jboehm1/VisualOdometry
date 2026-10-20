package dev.jboehm.vectorvio;
import java.util.List;
/** Closest point on the local calibrated GPX polyline. No GNSS truth or pose updates. */
final class GpxWorldCue {
    static double[] nearest(List<double[]> edges,double x,double z){
        double best=Double.POSITIVE_INFINITY;double[] result=null;
        if(!Double.isFinite(x)||!Double.isFinite(z))return null;
        for(double[] edge:edges){
            if(edge.length!=4)continue;
            boolean good=true;for(double v:edge)good&=Double.isFinite(v);
            if(!good)continue;
            double vx=edge[2]-edge[0],vz=edge[3]-edge[1],sq=vx*vx+vz*vz;
            if(sq<1e-12)continue;
            double t=Math.max(0,Math.min(1,((x-edge[0])*vx+(z-edge[1])*vz)/sq));
            double nx=edge[0]+t*vx,nz=edge[1]+t*vz,dist=Math.hypot(nx-x,nz-z);
            if(dist<best){best=dist;result=new double[]{nx,nz,dist};}
        }
        return result;
    }
    private GpxWorldCue(){}
}
