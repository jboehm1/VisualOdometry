package dev.jboehm.vectorvio;

import java.util.*;

/** Display-only GPX projection on OBSERVED upward planes; never uses camera height as ground. */
final class GpxGroundProjector {
    static List<float[]> project(List<double[]> edges,double cameraY,List<GroundTrail.Floor> floors){
        ArrayList<float[]> result=new ArrayList<>();int budget=2048;
        for(double[] edge:edges){
            if(edge.length!=4)continue;
            boolean finite=true;for(double v:edge)finite&=Double.isFinite(v);if(!finite)continue;
            double length=Math.hypot(edge[2]-edge[0],edge[3]-edge[1]);
            if(length<1e-6||length>61)continue;
            int parts=Math.max(1,(int)Math.ceil(length/.5));
            for(int i=0;i<parts&&budget-->0;i++){
                double a=(double)i/parts,b=(double)(i+1)/parts;
                double ax=edge[0]+a*(edge[2]-edge[0]),az=edge[1]+a*(edge[3]-edge[1]);
                double bx=edge[0]+b*(edge[2]-edge[0]),bz=edge[1]+b*(edge[3]-edge[1]);
                // A single ARCore plane footprint is convex. Both endpoints must lie on it.
                for(GroundTrail.Floor floor:floors){
                    List<GroundTrail.Floor> one=Collections.singletonList(floor);
                    float ay=GroundTrail.floorY(new Odometer.Point(ax,cameraY,az),one);
                    float by=GroundTrail.floorY(new Odometer.Point(bx,cameraY,bz),one);
                    if(Float.isFinite(ay)&&Float.isFinite(by)){
                        result.add(new float[]{(float)ax,ay+.035f,(float)az,(float)bx,by+.035f,(float)bz});break;
                    }
                }
            }
            if(budget<=0)break;
        }
        return result;
    }
}
