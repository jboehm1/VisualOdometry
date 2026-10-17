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
    void clear(){floor.size=wall.size=points.size=route.size=0;}
    void add(int kind,float[] xyz){
        if(xyz==null)return;for(float v:xyz)if(!Float.isFinite(v))return;
        if(kind<2){Batch b=kind==0?floor:wall;for(int i=3;i+5<xyz.length;i+=3){b.vertex(xyz,0);b.vertex(xyz,i);b.vertex(xyz,i+3);}}
        else if(kind==2&&xyz.length==3)points.vertex(xyz,0);
        else if(kind==3&&xyz.length==6){
            float dx=xyz[3]-xyz[0],dz=xyz[5]-xyz[2];double length=Math.hypot(dx,dz);if(length<1e-6)return;
            float nx=(float)(-dz/length*.04),nz=(float)(dx/length*.04);
            route.add(xyz[0]+nx,xyz[1],xyz[2]+nz);route.add(xyz[0]-nx,xyz[1],xyz[2]-nz);route.add(xyz[3]+nx,xyz[4],xyz[5]+nz);
            route.add(xyz[3]+nx,xyz[4],xyz[5]+nz);route.add(xyz[0]-nx,xyz[1],xyz[2]-nz);route.add(xyz[3]-nx,xyz[4],xyz[5]-nz);
        }
    }
}
