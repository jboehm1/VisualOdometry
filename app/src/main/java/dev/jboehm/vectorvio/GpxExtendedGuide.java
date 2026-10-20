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
    /** Visible-only distant dashes. The complete route remains in RouteIndex;
     * a bounded view budget protects the phone without losing geographic data.
     * No ARCore Anchor is ever constructed from these lines. */
    static final int MAX_WORLD_DASHES=1400;
    static List<float[]> dashesInView(List<double[]> edges,double cx,double cy,double cz,
                                      float stableY,List<GroundTrail.Floor> floors,float[] vp){
        ArrayList<float[]> out=new ArrayList<>();
        if(!Float.isFinite(stableY)||vp==null||vp.length!=16)return out;
        for(double[] edge:edges){
            if(edge==null||edge.length!=4||out.size()>=MAX_WORLD_DASHES)break;
            boolean finite=true;for(double v:edge)finite&=Double.isFinite(v);
            if(!finite)continue;
            double dx=edge[2]-edge[0],dz=edge[3]-edge[1],len=Math.hypot(dx,dz);
            if(len<.001||len>100)continue;
            float[] a={(float)edge[0],stableY+.10f,(float)edge[1]};
            float[] b={(float)edge[2],stableY+.10f,(float)edge[3]};
            if(Projection.line(vp,a,b)==null)continue;
            if(Float.isFinite(GroundTrail.floorY(edge[0],cy,edge[1],floors))&&
               Float.isFinite(GroundTrail.floorY(edge[2],cy,edge[3],floors)))continue;
            double distance=Math.hypot((edge[0]+edge[2])*.5-cx,(edge[1]+edge[3])*.5-cz);
            double step=distance<120?4.5:distance<1000?12:distance<6000?24:48;
            int parts=Math.max(1,(int)Math.ceil(len/step));
            for(int i=0;i<parts&&out.size()<MAX_WORLD_DASHES;i++){
                double t=(double)i/parts,u=Math.min(1,(i+.62)/parts);
                out.add(new float[]{
                    (float)(edge[0]+dx*t),stableY+.10f,(float)(edge[1]+dz*t),
                    (float)(edge[0]+dx*u),stableY+.10f,(float)(edge[1]+dz*u)});
            }
        }
        return out;
    }
    private GpxExtendedGuide(){}
}
