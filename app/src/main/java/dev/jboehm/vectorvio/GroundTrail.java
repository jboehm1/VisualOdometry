package dev.jboehm.vectorvio;
import java.util.List;
/** Display-only floor selection; camera measurements remain unchanged. */
final class GroundTrail {
    static final class Floor {
        final float y;final float[] xz;
        Floor(float y,float[] xz){this.y=y;this.xz=xz;}
    }
    static float floorY(Odometer.Point p,List<Floor> floors){
        float best=Float.NaN;
        for(Floor f:floors){
            double height=p.y-f.y;if(!Float.isFinite(f.y)||height<.6||height>2.7)continue;
            boolean inside=false;int n=f.xz.length/2;
            for(int i=0,j=n-1;i<n;j=i++){
                double xi=f.xz[2*i],zi=f.xz[2*i+1],xj=f.xz[2*j],zj=f.xz[2*j+1];
                if((zi>p.z)!=(zj>p.z)&&p.x<(xj-xi)*(p.z-zi)/(zj-zi)+xi)inside=!inside;
            }
            if(inside&&(!Float.isFinite(best)||f.y<best))best=f.y;
        }
        return best;
    }
}
