package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Image-only feature tracking and a small relative map for the deterministic replay. */
final class SyntheticVision {
    static final int WIDTH=320, HEIGHT=240;
    private static final int MAX_FEATURES=120, MAX_MAP=1400, BORDER=8;
    private final ArrayList<Point> previous=new ArrayList<>();
    private final ArrayList<MapPoint> map=new ArrayList<>();
    private final ArrayList<float[]> path=new ArrayList<>();
    private float mapX,mapZ;
    private int frames;
    private int[] oldPixels;

    static final class Point { final float x,y; Point(float x,float y){this.x=x;this.y=y;} }
    static final class MapPoint { final float x,z,depth; MapPoint(float x,float z,float depth){this.x=x;this.z=z;this.depth=depth;} }
    static final class Result {
        final List<Point> tracked; final List<MapPoint> points; final List<float[]> path;
        final int detected,trackedCount; final float x,z;
        Result(List<Point> t,List<MapPoint> m,List<float[]> p,int d,float x,float z){tracked=t;points=m;path=p;detected=d;trackedCount=t.size();this.x=x;this.z=z;}
    }

    /** Input is the rendered camera image only. No simulator pose, scene geometry or IMU is read here. */
    Result process(int[] pixels,int width,int height) {
        if(pixels==null||width<32||height<32||pixels.length<width*height) return snapshot(Collections.<Point>emptyList(),0);
        ArrayList<Point> candidates=detect(pixels,width,height);
        ArrayList<Point> tracks=new ArrayList<>();
        ArrayList<Point> currentTracks=new ArrayList<>();
        ArrayList<Float> dxs=new ArrayList<>(),dys=new ArrayList<>();
        if(oldPixels!=null&&width==WIDTH&&height==HEIGHT) {
            for(Point old:previous) {
                Point best=match(old,oldPixels,pixels,width,height);
                if(best!=null){tracks.add(best);currentTracks.add(best);dxs.add(best.x-old.x);dys.add(best.y-old.y);}
            }
        }
        // Integrate robust image motion with a conservative scale prior. This is intentionally a
        // relative visual odometer: its map is reconstructed from pixel correspondences.
        if(tracks.size()>=5){mapX-=median(dxs)*.018f;mapZ+=median(dys)*.018f;}
        if(frames%3==0)for(Point p:candidates){
            boolean near=false;for(Point q:currentTracks)if(distance2(p,q)<49){near=true;break;}
            if(!near){currentTracks.add(p);if(currentTracks.size()>=MAX_FEATURES)break;}
        }
        for(Point p:tracks){
            float depth=Math.max(.7f,Math.min(8f,3.0f+(p.y-HEIGHT*.42f)*.035f));
            map.add(new MapPoint(mapX+(p.x-WIDTH*.5f)*depth/190f,mapZ+(HEIGHT*.48f-p.y)*depth/190f,depth));
        }
        while(map.size()>MAX_MAP)map.remove(0);
        if(frames%2==0){path.add(new float[]{mapX,mapZ});while(path.size()>500)path.remove(0);}
        previous.clear();previous.addAll(currentTracks);
        oldPixels=pixels.clone();frames++;
        return new Result(new ArrayList<>(tracks),new ArrayList<>(map),new ArrayList<>(path),candidates.size(),mapX,mapZ);
    }

    void reset(){previous.clear();map.clear();path.clear();oldPixels=null;mapX=mapZ=0;frames=0;}
    private Result snapshot(List<Point> t,int detected){return new Result(t,new ArrayList<>(map),new ArrayList<>(path),detected,mapX,mapZ);}
    private ArrayList<Point> detect(int[] p,int w,int h){
        ArrayList<Point> out=new ArrayList<>();ArrayList<Scored> scores=new ArrayList<>();
        for(int y=BORDER;y<h-BORDER;y+=2)for(int x=BORDER;x<w-BORDER;x+=2){
            float xx=0,yy=0,xy=0;
            for(int wy=-1;wy<=1;wy++)for(int wx=-1;wx<=1;wx++){
                int gx=gray(p[(y+wy)*w+x+wx+1])-gray(p[(y+wy)*w+x+wx-1]);
                int gy=gray(p[(y+wy+1)*w+x+wx])-gray(p[(y+wy-1)*w+x+wx]);
                xx+=gx*gx;yy+=gy*gy;xy+=gx*gy;
            }
            float trace=xx+yy,score=xx*yy-xy*xy-.04f*trace*trace;
            if(score>3_000_000)scores.add(new Scored(x,y,score));
        }
        Collections.sort(scores,(a,b)->Float.compare(b.score,a.score));
        for(Scored s:scores){boolean close=false;for(Point q:out)if(distance2(q,new Point(s.x,s.y))<100){close=true;break;}if(!close){out.add(new Point(s.x,s.y));if(out.size()>=MAX_FEATURES)break;}}
        return out;
    }
    private Point match(Point p,int[] old,int[] now,int w,int h){
        int ox=Math.round(p.x),oy=Math.round(p.y);if(ox<5||oy<5||ox>=w-5||oy>=h-5)return null;
        int best=1000000,bx=0,by=0;
        for(int dy=-7;dy<=7;dy++)for(int dx=-9;dx<=9;dx++){int x=ox+dx,y=oy+dy;if(x<5||y<5||x>=w-5||y>=h-5)continue;
            int sad=0;for(int py=-2;py<=2;py++)for(int px=-2;px<=2;px++)sad+=Math.abs(gray(old[(oy+py)*w+ox+px])-gray(now[(y+py)*w+x+px]));
            if(sad<best){best=sad;bx=x;by=y;}}
        return best<1500?new Point(bx,by):null;
    }
    private int gray(int c){return (((c>>16)&255)*3+((c>>8)&255)*6+(c&255))/10;}
    private float median(ArrayList<Float> v){Collections.sort(v);return v.get(v.size()/2);}
    private float distance2(Point a,Point b){float x=a.x-b.x,y=a.y-b.y;return x*x+y*y;}
    private static final class Scored {final int x,y;final float score;Scored(int x,int y,float s){this.x=x;this.y=y;score=s;}}
}
