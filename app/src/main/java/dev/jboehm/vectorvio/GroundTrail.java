package dev.jboehm.vectorvio;
import java.util.List;
/** Display-only floor selection; camera measurements remain unchanged. */
final class GroundTrail {
    static final class Floor {
        final float x,y,z,nx,ny,nz;final float[] xz;final boolean validFootprint;
        Floor(float y,float[] xz){this(0,y,0,0,1,0,xz);}
        Floor(float x,float y,float z,float nx,float ny,float nz,float[] xz){
            this.x=x;this.y=y;this.z=z;this.nx=nx;this.ny=ny;this.nz=nz;this.xz=xz;
            boolean valid=xz!=null&&xz.length>=6&&xz.length%2==0;
            if(valid)for(float v:xz)if(!Float.isFinite(v)){valid=false;break;}
            validFootprint=valid;
        }
        float height(double px,double pz){
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(nx)||!Float.isFinite(ny)||!Float.isFinite(nz)||ny<.5f)return Float.NaN;
            return (float)(y-(nx*(px-x)+nz*(pz-z))/ny);
        }
    }
    static float floorY(Odometer.Point p,List<Floor> floors){
        float best=Float.NaN;
        for(Floor f:floors){
            if(!f.validFootprint)continue;
            float y=f.height(p.x,p.z);double height=p.y-y;
            if(!Float.isFinite(y)||height<.1||height>2.7)continue;
            boolean inside=false;int n=f.xz.length/2;
            for(int i=0,j=n-1;i<n;j=i++){
                double xi=f.xz[2*i],zi=f.xz[2*i+1],xj=f.xz[2*j],zj=f.xz[2*j+1];
                if((zi>p.z)!=(zj>p.z)&&p.x<(xj-xi)*(p.z-zi)/(zj-zi)+xi)inside=!inside;
            }
            if(inside&&(!Float.isFinite(best)||y<best))best=y;
        }
        return best;
    }
}
