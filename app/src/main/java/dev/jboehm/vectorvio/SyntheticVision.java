package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small deterministic visual-inertial odometer for the replay screen.
 *
 * It accepts only camera pixels and synthetic IMU samples. It does not read the
 * scene renderer, simulated camera pose, corridor model, or ground-truth route.
 * Rotation comes from gyro integration, translation is primarily estimated from
 * tracked image motion, and accelerometer integration provides a low-weight
 * velocity prior. Landmarks are initialized by two-view ray triangulation after
 * sufficient estimated camera motion. This is a teaching/debugging pipeline,
 * not a production-grade or metrically validated SLAM system.
 */
final class SyntheticVision {
    static final int WIDTH=480, HEIGHT=360;
    private static final int MAX_FEATURES=120, MAX_MAP=1400, BORDER=8;
    private static final float FOCAL=190f, DEFAULT_DEPTH=3.0f;
    private final ArrayList<Point> previous=new ArrayList<>();
    private final ArrayList<MapPoint> map=new ArrayList<>();
    private final ArrayList<float[]> path=new ArrayList<>();
    private final Map<Integer,Observation> observations=new HashMap<>();
    private final Set<Integer> mappedIds=new HashSet<>();
    private float mapX,mapY,mapZ,yaw,velocityX,velocityY,velocityZ;
    private int frames,nextId;
    private int[] oldPixels;
    private long previousTimeNs=-1;
    private float previousGyroYaw;

    /** Sensor contract contains no ground-truth position, orientation, or scene fields. */
    static final class SensorSample {
        final long timeNs;final float ax,ay,az,gx,gy,gz;
        SensorSample(long timeNs,float ax,float ay,float az,float gx,float gy,float gz){this.timeNs=timeNs;this.ax=ax;this.ay=ay;this.az=az;this.gx=gx;this.gy=gy;this.gz=gz;}
    }

    static final class Point { final int id; final float x,y; Point(int id,float x,float y){this.id=id;this.x=x;this.y=y;} }
    static final class MapPoint { final int id; final float x,y,z,depth; MapPoint(int id,float x,float y,float z,float depth){this.id=id;this.x=x;this.y=y;this.z=z;this.depth=depth;} }
    private static final class Observation {
        final float x,y,z,yaw,u,v;
        Observation(float x,float y,float z,float yaw,float u,float v){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.u=u;this.v=v;}
    }
    static final class Result {
        final List<Point> tracked; final List<MapPoint> points; final List<float[]> path;
        final int detected,trackedCount; final float x,y,z,yaw;
        Result(List<Point> t,List<MapPoint> m,List<float[]> p,int d,float x,float y,float z,float yaw){tracked=t;points=m;path=p;detected=d;trackedCount=t.size();this.x=x;this.y=y;this.z=z;this.yaw=yaw;}
    }

    /** Compatibility entry point for isolated image-tracker tests (no IMU). */
    Result process(int[] pixels,int width,int height) { return process(pixels,width,height,(SensorSample)null); }

    /** Input is the rendered camera image and a sensor sample only. */
    Result process(int[] pixels,int width,int height,SensorSample imu) {
        if(pixels==null||width<32||height<32||pixels.length<width*height) return snapshot(Collections.<Point>emptyList(),0);
        float dt=previousTimeNs<0||imu==null?0:Math.max(0,Math.min(.15f,(imu.timeNs-previousTimeNs)*1e-9f));
        float gyroYaw=imu==null?0:imu.gy;
        float deltaYaw=dt>0?(previousGyroYaw+gyroYaw)*.5f*dt:0;
        yaw=wrap(yaw+deltaYaw);

        ArrayList<Point> candidates=detect(pixels,width,height);
        ArrayList<Point> tracks=new ArrayList<>(),currentTracks=new ArrayList<>();
        ArrayList<Float> dxs=new ArrayList<>(),dys=new ArrayList<>(),radial=new ArrayList<>();
        if(oldPixels!=null&&width==WIDTH&&height==HEIGHT) {
            for(Point old:previous) {
                Point now=match(old,oldPixels,pixels,width,height);
                if(now==null)continue;
                tracks.add(now);currentTracks.add(now);dxs.add(now.x-old.x);dys.add(now.y-old.y);
                float ox=old.x-WIDTH*.5f,oy=old.y-HEIGHT*.5f,nx=now.x-WIDTH*.5f,ny=now.y-HEIGHT*.5f;
                float r2=ox*ox+oy*oy;
                if(r2>1800)radial.add(((nx-ox)*ox+(ny-oy)*oy)/r2);
            }
        }

        // Image flow estimates camera translation. Gyro-predicted rotational flow
        // is removed before the horizontal component is interpreted as translation.
        float visualDx=0,visualDz=0,visualDy=0;
        if(tracks.size()>=5) {
            float mdx=median(dxs),mdy=median(dys),div=radial.isEmpty()?0:median(radial);
            float lateral=-(mdx+FOCAL*deltaYaw)*DEFAULT_DEPTH/FOCAL;
            float forward=clamp(div*DEFAULT_DEPTH,-.10f,.10f);
            visualDy=clamp(mdy*DEFAULT_DEPTH/FOCAL,-.05f,.05f);
            float c=(float)Math.cos(yaw),s=(float)Math.sin(yaw);
            visualDx=c*lateral-s*forward;
            visualDz=-s*lateral-c*forward;
        }

        if(dt>0&&imu!=null) {
            // Synthetic accelerometer specific force has gravity on phone Y.
            // Keep inertial velocity as a weak prior; image motion anchors scale.
            float ax=(float)(Math.cos(yaw)*imu.ax-Math.sin(yaw)*imu.az);
            float az=(float)(-Math.sin(yaw)*imu.ax-Math.cos(yaw)*imu.az);
            velocityX=clamp(velocityX+ax*dt,-1.2f,1.2f);
            velocityY=clamp(velocityY+(imu.ay-9.80665f)*dt,-.5f,.5f);
            velocityZ=clamp(velocityZ+az*dt,-1.2f,1.2f);
            if(tracks.size()>=5) {
                float wx=visualDx/dt,wz=visualDz/dt,wy=visualDy/dt;
                velocityX=.88f*wx+.12f*velocityX;velocityZ=.88f*wz+.12f*velocityZ;velocityY=.82f*wy+.18f*velocityY;
                mapX+=visualDx;mapZ+=visualDz;mapY+=visualDy;
            } else {
                mapX+=velocityX*dt*.10f;mapY+=velocityY*dt*.10f;mapZ+=velocityZ*dt*.10f;
            }
        } else if(tracks.size()>=5) { mapX+=visualDx;mapZ+=visualDz;mapY+=visualDy; }

        if(frames%3==0)for(Point p:candidates) {
            boolean near=false;for(Point q:currentTracks)if(distance2(p,q)<64){near=true;break;}
            if(!near){currentTracks.add(new Point(nextId++,p.x,p.y));if(currentTracks.size()>=MAX_FEATURES)break;}
        }

        for(Point p:tracks) {
            if(mappedIds.contains(p.id))continue;
            Observation old=observations.get(p.id);
            if(old==null)continue;
            Observation now=new Observation(mapX,mapY,mapZ,yaw,p.x,p.y);
            MapPoint triangulated=triangulate(p.id,old,now);
            if(triangulated!=null){mappedIds.add(p.id);map.add(triangulated);}
            else observations.put(p.id,now);
        }
        for(Point p:currentTracks)if(!observations.containsKey(p.id))observations.put(p.id,new Observation(mapX,mapY,mapZ,yaw,p.x,p.y));
        while(map.size()>MAX_MAP){mappedIds.remove(map.remove(0).id);}
        // Do not show a made-up origin marker before actual visual tracks exist.
        if(tracks.size()>=5&&frames%2==0){path.add(new float[]{mapX,mapY,mapZ});while(path.size()>500)path.remove(0);}
        previous.clear();previous.addAll(currentTracks);oldPixels=pixels.clone();frames++;
        if(imu!=null){previousTimeNs=imu.timeNs;previousGyroYaw=gyroYaw;}
        return new Result(new ArrayList<>(tracks),new ArrayList<>(map),new ArrayList<>(path),candidates.size(),mapX,mapY,mapZ,yaw);
    }

    void reset(){previous.clear();map.clear();path.clear();observations.clear();mappedIds.clear();oldPixels=null;mapX=mapY=mapZ=yaw=velocityX=velocityY=velocityZ=previousGyroYaw=0;previousTimeNs=-1;frames=nextId=0;}
    private Result snapshot(List<Point> t,int detected){return new Result(t,new ArrayList<>(map),new ArrayList<>(path),detected,mapX,mapY,mapZ,yaw);}

    /** Triangulate the two camera rays in the estimator's own coordinate frame. */
    private MapPoint triangulate(int id,Observation a,Observation b) {
        float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
        float baseline=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(baseline<.10f)return null;
        float[] da=ray(a),db=ray(b);float dot=dot(da,db);if(1-Math.abs(dot)<.00035f)return null;
        float[] w={a.x-b.x,a.y-b.y,a.z-b.z};
        float d=dot(da,w),e=dot(db,w),den=1-dot*dot;if(den<1e-5f)return null;
        float ta=(dot*e-d)/den,tb=(e-dot*d)/den;if(ta<=.15f||tb<=.15f||ta>12||tb>12)return null;
        float ax=a.x+ta*da[0],ay=a.y+ta*da[1],az=a.z+ta*da[2];
        float bx=b.x+tb*db[0],by=b.y+tb*db[1],bz=b.z+tb*db[2];
        float mx=(ax+bx)*.5f,my=(ay+by)*.5f,mz=(az+bz)*.5f;
        float miss=(float)Math.sqrt((ax-bx)*(ax-bx)+(ay-by)*(ay-by)+(az-bz)*(az-bz));
        if(miss>.22f)return null;
        return new MapPoint(id,mx,my,mz,(ta+tb)*.5f);
    }
    private float[] ray(Observation o) {
        float right=(o.u-WIDTH*.5f)/FOCAL,up=(HEIGHT*.5f-o.v)/FOCAL;
        float norm=(float)Math.sqrt(right*right+up*up+1);right/=norm;up/=norm;float forward=1/norm;
        float c=(float)Math.cos(o.yaw),s=(float)Math.sin(o.yaw);
        return new float[]{c*right-s*forward,up,-s*right-c*forward};
    }
    private float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private float wrap(float a){while(a>Math.PI)a-=2*Math.PI;while(a< -Math.PI)a+=2*Math.PI;return a;}
    private ArrayList<Point> detect(int[] p,int w,int h){
        ArrayList<Point> out=new ArrayList<>();ArrayList<Scored> scores=new ArrayList<>();
        for(int y=BORDER;y<h-BORDER;y+=2)for(int x=BORDER;x<w-BORDER;x+=2){
            float xx=0,yy=0,xy=0;
            for(int wy=-1;wy<=1;wy++)for(int wx=-1;wx<=1;wx++){
                int gx=gray(p[(y+wy)*w+x+wx+1])-gray(p[(y+wy)*w+x+wx-1]);
                int gy=gray(p[(y+wy+1)*w+x+wx])-gray(p[(y+wy-1)*w+x+wx]);
                xx+=gx*gx;yy+=gy*gy;xy+=gx*gy;
            }
            float trace=xx+yy,score=xx*yy-xy*xy-.04f*trace*trace;if(score>3_000_000)scores.add(new Scored(x,y,score));
        }
        Collections.sort(scores,(a,b)->Float.compare(b.score,a.score));
        for(Scored s:scores){boolean close=false;for(Point q:out)if(distance2(q,new Point(-1,s.x,s.y))<100){close=true;break;}if(!close){out.add(new Point(-1,s.x,s.y));if(out.size()>=MAX_FEATURES)break;}}
        return out;
    }
    private Point match(Point p,int[] old,int[] now,int w,int h){
        int ox=Math.round(p.x),oy=Math.round(p.y);if(ox<5||oy<5||ox>=w-5||oy>=h-5)return null;
        int best=1000000,bx=0,by=0;
        for(int dy=-9;dy<=9;dy++)for(int dx=-11;dx<=11;dx++){int x=ox+dx,y=oy+dy;if(x<5||y<5||x>=w-5||y>=h-5)continue;
            int sad=0;for(int py=-2;py<=2;py++)for(int px=-2;px<=2;px++)sad+=Math.abs(gray(old[(oy+py)*w+ox+px])-gray(now[(y+py)*w+x+px]));
            if(sad<best){best=sad;bx=x;by=y;}}
        return best<1500?new Point(p.id,bx,by):null;
    }
    private int gray(int c){return (((c>>16)&255)*3+((c>>8)&255)*6+(c&255))/10;}
    private float median(ArrayList<Float> v){Collections.sort(v);return v.get(v.size()/2);}
    private float distance2(Point a,Point b){float x=a.x-b.x,y=a.y-b.y;return x*x+y*y;}
    private static final class Scored {final int x,y;final float score;Scored(int x,int y,float s){this.x=x;this.y=y;score=s;}}
}
