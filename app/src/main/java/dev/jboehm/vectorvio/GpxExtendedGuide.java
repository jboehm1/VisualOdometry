package dev.jboehm.vectorvio;
import java.util.ArrayList;
import java.util.List;
/**
 * A dashed GPX preview ABOVE UNKNOWN terrain, explicit approximation.
 * The stable height is a visual fallback, never a detected ground observation.
 */
final class GpxExtendedGuide {
    static final int MAX_DASHES=720;
    static List<float[]> dashes(List<double[]> edges,double cx,double cy,double cz,
                                float stableY,List<GroundTrail.Floor> floors){
        ArrayList<float[]> out=new ArrayList<>();
        if(!Float.isFinite(stableY))return out;
        for(double[] edge:edges){
            if(edge.length!=4||out.size()>=MAX_DASHES)break;
            boolean finite=true;for(double v:edge)finite&=Double.isFinite(v);
            if(!finite)continue;
            double len=Math.hypot(edge[2]-edge[0],edge[3]-edge[1]);
            if(len<.001||len>60)continue;
            int parts=Math.max(1,(int)Math.ceil(len/2.6));
            for(int i=0;i<parts&&out.size()<MAX_DASHES;i++){
                double t=(double)i/parts,u=Math.min(1,(i+.61)/parts);
                double ax=edge[0]+(edge[2]-edge[0])*t,az=edge[1]+(edge[3]-edge[1])*t;
                double bx=edge[0]+(edge[2]-edge[0])*u,bz=edge[1]+(edge[3]-edge[1])*u;
                if(Math.min(Math.hypot(ax-cx,az-cz),Math.hypot(bx-cx,bz-cz))>100)continue;
                if(Float.isFinite(GroundTrail.floorY(ax,cy,az,floors))&&
                   Float.isFinite(GroundTrail.floorY(bx,cy,bz,floors)))continue;
                out.add(new float[]{(float)ax,stableY+.10f,(float)az,
                    (float)bx,stableY+.10f,(float)bz});
            }
        }
        return out;
    }
    private GpxExtendedGuide(){}
}
