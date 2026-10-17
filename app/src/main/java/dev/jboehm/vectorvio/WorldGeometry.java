package dev.jboehm.vectorvio;
import java.util.Arrays;
/** Reusable CPU staging arrays; no camera projection or measurement modification. */
final class WorldGeometry {
    static final class Batch {
        float[] data=new float[256];int size;
        void add(float x,float y,float z){if(size+3>data.length)data=Arrays.copyOf(data,Math.max(size+3,data.length*2));data[size++]=x;data[size++]=y;data[size++]=z;}
        void vertex(float[] xyz,int i){add(xyz[i],xyz[i+1],xyz[i+2]);}
    }
    final Batch floor=new Batch(),wall=new Batch(),points=new Batch(),route=new Batch();
    private static final float HALF_WIDTH=.04f;
    private boolean chain;
    private int previousBase;
    private float endX,endY,endZ,lastNx,lastNz,startX,startY,startZ;
    void clear(){floor.size=wall.size=points.size=route.size=0;chain=false;}
    private void cap(float x,float y,float z){
        for(int i=0;i<12;i++){
            double a=2*Math.PI*i/12,b=2*Math.PI*(i+1)/12;
            route.add(x,y,z);route.add(x+(float)Math.cos(a)*HALF_WIDTH,y,z+(float)Math.sin(a)*HALF_WIDTH);
            route.add(x+(float)Math.cos(b)*HALF_WIDTH,y,z+(float)Math.sin(b)*HALF_WIDTH);
        }
    }
    /** Finalize just two rounded ends per continuous chain, not a disk per sample. */
    void finishRoute(){if(chain){cap(startX,startY,startZ);cap(endX,endY,endZ);chain=false;}}
    private void previousEnd(float x,float y,float z,float nx,float nz){
        for(int offset=6;offset<=9;offset+=3){route.data[previousBase+offset]=x+nx;route.data[previousBase+offset+1]=y;route.data[previousBase+offset+2]=z+nz;}
        route.data[previousBase+15]=x-nx;route.data[previousBase+16]=y;route.data[previousBase+17]=z-nz;
    }
    void add(int kind,float[] xyz){
        if(xyz==null)return;for(float v:xyz)if(!Float.isFinite(v))return;
        if(kind<2){Batch b=kind==0?floor:wall;for(int i=3;i+5<xyz.length;i+=3){b.vertex(xyz,0);b.vertex(xyz,i);b.vertex(xyz,i+3);}}
        else if(kind==2&&xyz.length==3)points.vertex(xyz,0);
        else if(kind==3&&xyz.length==6){
            float dx=xyz[3]-xyz[0],dz=xyz[5]-xyz[2];double length=Math.hypot(dx,dz);if(length<1e-6)return;
            float nx=(float)(-dz/length*HALF_WIDTH),nz=(float)(dx/length*HALF_WIDTH);
            float startNx=nx,startNz=nz;
            boolean joins=chain&&Math.abs(endX-xyz[0])<1e-5f&&Math.abs(endY-xyz[1])<1e-5f&&Math.abs(endZ-xyz[2])<1e-5f;
            float dot=(lastNx*nx+lastNz*nz)/(HALF_WIDTH*HALF_WIDTH);
            if(joins&&dot>-.5f){
                float bx=lastNx+nx,bz=lastNz+nz;
                double denominator=(double)bx*nx+(double)bz*nz;
                if(denominator>1e-9){
                    float scale=(float)(HALF_WIDTH*HALF_WIDTH/denominator);
                    startNx=bx*scale;startNz=bz*scale;
                    previousEnd(xyz[0],xyz[1],xyz[2],startNx,startNz);
                }
            }else{
                finishRoute();startX=xyz[0];startY=xyz[1];startZ=xyz[2];
            }
            previousBase=route.size;
            route.add(xyz[0]+startNx,xyz[1],xyz[2]+startNz);route.add(xyz[0]-startNx,xyz[1],xyz[2]-startNz);route.add(xyz[3]+nx,xyz[4],xyz[5]+nz);
            route.add(xyz[3]+nx,xyz[4],xyz[5]+nz);route.add(xyz[0]-startNx,xyz[1],xyz[2]-startNz);route.add(xyz[3]-nx,xyz[4],xyz[5]-nz);
            endX=xyz[3];endY=xyz[4];endZ=xyz[5];lastNx=nx;lastNz=nz;chain=true;
        }
    }
}
