package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.List;

/** Column-major OpenGL projection with homogeneous clipping before perspective divide. */
final class Projection {
    private static float[] clipPoint(float[] m,float x,float y,float z) {
        float[] out=new float[4];
        for(int i=0;i<4;i++)out[i]=m[i]*x+m[4+i]*y+m[8+i]*z+m[12+i];
        return out;
    }
    static float[] point(float[] m,float x,float y,float z) {
        float[] v=clipPoint(m,x,y,z);
        for(int plane=0;plane<7;plane++)if(distance(v,plane)<0)return null;
        return screen(v);
    }
    private static float distance(float[] v,int plane) {
        switch(plane) {
            case 0:return v[3]-.0001f;
            case 1:return v[3]+v[0]; case 2:return v[3]-v[0];
            case 3:return v[3]+v[1]; case 4:return v[3]-v[1];
            case 5:return v[3]+v[2];default:return v[3]-v[2];
        }
    }
    private static float[] screen(float[] v){return new float[]{.5f+.5f*v[0]/v[3],.5f-.5f*v[1]/v[3]};}
    private static float[] intersect(float[] a,float[] b,float da,float db) {
        float t=da/(da-db);float[] p=new float[4];
        for(int j=0;j<4;j++)p[j]=a[j]+t*(b[j]-a[j]);return p;
    }
    static float[] polygon(float[] m,float[] xyz) {
        List<float[]> vertices=new ArrayList<>();
        for(int i=0;i<xyz.length;i+=3)vertices.add(clipPoint(m,xyz[i],xyz[i+1],xyz[i+2]));
        for(int plane=0;plane<7 && !vertices.isEmpty();plane++) {
            List<float[]> output=new ArrayList<>();float[] a=vertices.get(vertices.size()-1);
            float da=distance(a,plane);
            for(float[] b:vertices) {
                float db=distance(b,plane);
                if((da>=0)!=(db>=0))output.add(intersect(a,b,da,db));
                if(db>=0)output.add(b);
                a=b;da=db;
            }
            vertices=output;
        }
        float[] xy=new float[vertices.size()*2];int i=0;
        for(float[] v:vertices){float[] p=screen(v);xy[i++]=p[0];xy[i++]=p[1];}
        return xy;
    }
    static float[] line(float[] m,float[] a,float[] b) {
        float[] p=clipPoint(m,a[0],a[1],a[2]),q=clipPoint(m,b[0],b[1],b[2]);
        for(int plane=0;plane<7;plane++) {
            float da=distance(p,plane),db=distance(q,plane);
            if(da<0 && db<0)return null;
            if(da<0)p=intersect(p,q,da,db);
            else if(db<0)q=intersect(p,q,da,db);
        }
        float[] x=screen(p),y=screen(q);return new float[]{x[0],x[1],y[0],y[1]};
    }
}
