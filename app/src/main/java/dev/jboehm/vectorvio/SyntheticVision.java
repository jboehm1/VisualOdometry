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
 * tracked image motion. During visually confirmed forward planar motion,
 * lateral acceleration and gyro turn rate initialize metric scale; longitudinal
 * acceleration then supports velocity. This motion prior is not general 6-DoF VIO. Landmarks are initialized by two-view ray triangulation after
 * sufficient estimated camera motion. This is a teaching/debugging pipeline,
 * not a production-grade or metrically validated SLAM system.
 */
final class SyntheticVision {
    static final int WIDTH=ReplayCamera.WIDTH, HEIGHT=ReplayCamera.HEIGHT;
    private static final int MAX_FEATURES=120, MAX_MAP=1400, BORDER=8;
    private static final float FOCAL=ReplayCamera.FOCAL, DEFAULT_DEPTH=3.0f;
    private final ArrayList<Point> previous=new ArrayList<>();
    private final ArrayList<MapPoint> map=new ArrayList<>();
    private final ArrayList<float[]> path=new ArrayList<>();
    private final Map<Integer,Observation> observations=new HashMap<>();
    private final Set<Integer> mappedIds=new HashSet<>();
    private float mapX,mapY,mapZ,yaw;
    private int frames,nextId;
    private int[] oldPixels;
    private long previousTimeNs=-1;
    private float previousGyroYaw;
    private double distance;
    private float speed,metricScale=1,forwardVelocity;
    private final ArrayList<Float> scaleCandidates=new ArrayList<>();
    private boolean scaleInitialized;
    private final Map<Integer,MapPoint> landmarkById=new HashMap<>();

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
        final int detected,trackedCount; final float x,y,z,yaw; final double distance; final float speed,scale; final boolean metricInitialized;
        Result(List<Point> t,List<MapPoint> m,List<float[]> p,int d,float x,float y,float z,float yaw,double distance,float speed,boolean metricInitialized,float scale){tracked=t;points=m;path=p;detected=d;trackedCount=t.size();this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.distance=distance;this.speed=speed;this.metricInitialized=metricInitialized;this.scale=scale;}
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
                Point now=match(old,oldPixels,pixels,width,height,deltaYaw);
                if(now==null)continue;
                tracks.add(now);currentTracks.add(now);dxs.add(now.x-old.x);dys.add(now.y-old.y);
                float ox=old.x-WIDTH*.5f,oy=old.y-HEIGHT*.5f,nx=now.x-WIDTH*.5f,ny=now.y-HEIGHT*.5f;
                float r2=ox*ox+oy*oy;
                if(r2>1800)radial.add(((nx-ox)*ox+(ny-oy)*oy)/r2);
            }
        }

        // Calibrated rotational reprojection is removed per feature (not a constant
        // pixel offset). Translation is fitted robustly from both image coordinates.
        float[] motion=translation(tracks,deltaYaw);
        float lateral=motion[0],up=motion[1],forward=motion[2];
        if(!scaleInitialized&&dt>0&&imu!=null&&tracks.size()>=15&&Math.abs(gyroYaw)>.15f&&Math.abs(lateral)<Math.abs(forward)*.5f){
            // Metric initialization under observed forward planar motion. During a
            // sustained turn a_right = -yaw_rate * forward_speed. No scene size,
            // camera height, true speed, route or truth pose is supplied here.
            float inertialSpeed=-imu.ax/gyroYaw;
            if(inertialSpeed>.15f&&inertialSpeed<2.5f&&forward/dt>.1f){
                scaleCandidates.add(inertialSpeed/(forward/dt));
                if(scaleCandidates.size()>45)scaleCandidates.remove(0);
                if(scaleCandidates.size()>=10){
                    float correction=median(new ArrayList<>(scaleCandidates));
                    if(!scaleInitialized&&Float.isFinite(correction)&&correction>.1f&&correction<10){rescale(correction);metricScale*=correction;scaleInitialized=true;forwardVelocity=inertialSpeed;scaleCandidates.clear();}
                }
            }
        }
        if(!scaleInitialized){lateral*=metricScale;up*=metricScale;forward*=metricScale;}
        else if(motion[3]==0){lateral*=metricScale;up*=metricScale;forward*=metricScale;}
        if(scaleInitialized&&dt>0&&tracks.size()>=15&&forward>0&&Math.abs(lateral)<Math.abs(forward)*.5f){
            forwardVelocity=clamp(forwardVelocity-imu.az*dt,.0f,2.5f);
            if(Math.abs(gyroYaw)>.15f){float target=-imu.ax/gyroYaw;if(target>.15f&&target<2.5f)forwardVelocity=.85f*forwardVelocity+.15f*target;}
            // Explicit forward-planar inertial motion prior; enabled only after
            // visual confirmation and turn excitation, never from simulator truth.
            forward=.9f*forwardVelocity*dt+.1f*forward;
        }
        float c=(float)Math.cos(yaw),sn=(float)Math.sin(yaw);
        float visualDx=c*lateral-sn*forward,visualDz=-sn*lateral-c*forward;
        if(tracks.size()>=5){
            mapX+=visualDx;mapY+=up;mapZ+=visualDz;
            double step=Math.sqrt(visualDx*visualDx+up*up+visualDz*visualDz);
            distance+=step;if(dt>0)speed=.8f*speed+.2f*(float)(step/dt);
        }else speed=0;

        if(frames%3==0)for(Point p:candidates) {
            boolean near=false;for(Point q:currentTracks)if(distance2(p,q)<64){near=true;break;}
            if(!near){currentTracks.add(new Point(nextId++,p.x,p.y));if(currentTracks.size()>=MAX_FEATURES)break;}
        }

        for(Point p:tracks) {
            if(mappedIds.contains(p.id))continue;
            Observation old=observations.get(p.id);
            if(old==null)continue;
            Observation now=new Observation(mapX,mapY,mapZ,yaw,p.x,p.y);
            float bx=now.x-old.x,by=now.y-old.y,bz=now.z-old.z;
            // Keep the first key observation until the estimated baseline is
            // large enough. Replacing it every frame would prevent small but
            // steady camera motion from ever reaching triangulation parallax.
            if(bx*bx+by*by+bz*bz<.0625f)continue;
            MapPoint triangulated=triangulate(p.id,old,now);
            if(triangulated!=null){mappedIds.add(p.id);map.add(triangulated);landmarkById.put(p.id,triangulated);}
        }
        for(Point p:currentTracks)if(!observations.containsKey(p.id))observations.put(p.id,new Observation(mapX,mapY,mapZ,yaw,p.x,p.y));
        while(map.size()>MAX_MAP){MapPoint removed=map.remove(0);mappedIds.remove(removed.id);landmarkById.remove(removed.id);}
        // Do not show a made-up origin marker before actual visual tracks exist.
        if(tracks.size()>=5&&frames%2==0){path.add(new float[]{mapX,mapY,mapZ});while(path.size()>2400)path.remove(0);}
        if(frames%30==0){Set<Integer> active=new HashSet<>();for(Point p:currentTracks)active.add(p.id);observations.keySet().retainAll(active);}
        previous.clear();previous.addAll(currentTracks);oldPixels=pixels.clone();frames++;
        if(imu!=null){previousTimeNs=imu.timeNs;previousGyroYaw=gyroYaw;}
        return new Result(new ArrayList<>(tracks),new ArrayList<>(map),new ArrayList<>(path),candidates.size(),mapX,mapY,mapZ,yaw,distance,speed,scaleInitialized,metricScale);
    }

    private float[] rotatePixel(float x,float y,float angle){
        float u=(x-WIDTH*.5f)/FOCAL,v=(y-HEIGHT*.5f)/FOCAL,c=(float)Math.cos(angle),s=(float)Math.sin(angle),z=c-s*u;
        return new float[]{WIDTH*.5f+FOCAL*(c*u+s)/z,HEIGHT*.5f+FOCAL*v/z};
    }
    private float[] translation(ArrayList<Point> tracks,float rotation){
        if(tracks.size()<5)return new float[4];
        ArrayList<float[]> rows=new ArrayList<>();int known=0;
        for(Point p:tracks){
            Point old=null;for(Point q:previous)if(q.id==p.id){old=q;break;}if(old==null)continue;
            float[] predicted=rotatePixel(old.x,old.y,rotation);
            float u=(predicted[0]-WIDTH*.5f)/FOCAL,v=(predicted[1]-HEIGHT*.5f)/FOCAL,depth=DEFAULT_DEPTH;
            MapPoint m=landmarkById.get(p.id);
            if(m!=null){float dx=m.x-mapX,dz=m.z-mapZ;depth=-(float)Math.sin(yaw)*dx-(float)Math.cos(yaw)*dz;if(depth<.3f||depth>15)continue;known++;}
            rows.add(new float[]{u,v,(p.x-predicted[0])/FOCAL,(p.y-predicted[1])/FOCAL,depth,m==null?0:1});
        }
        boolean useKnown=known>=12;float[] estimate=new float[3];
        for(int iteration=0;iteration<4;iteration++){
            double[][] a=new double[3][3];double[] b=new double[3];
            for(float[] r:rows){if(useKnown&&r[5]==0)continue;
                double rx=(-estimate[0]+r[0]*estimate[2])/r[4]-r[2],ry=(estimate[1]+r[1]*estimate[2])/r[4]-r[3];
                double residual=Math.hypot(rx,ry),weight=iteration==0?1:Math.min(1,.006/Math.max(.00001,residual));
                accumulate(a,b,new double[]{-1,0,r[0]},r[2]*r[4],weight);
                accumulate(a,b,new double[]{0,1,r[1]},r[3]*r[4],weight);
            }
            estimate=solve(a,b);
        }
        return new float[]{clamp(estimate[0],-.15f,.15f),clamp(estimate[1],-.08f,.08f),clamp(estimate[2],-.20f,.20f),useKnown?1:0};
    }
    private void accumulate(double[][] a,double[] b,double[] row,double value,double weight){for(int i=0;i<3;i++){b[i]+=row[i]*value*weight;for(int j=0;j<3;j++)a[i][j]+=row[i]*row[j]*weight;}}
    private float[] solve(double[][] a,double[] b){
        for(int i=0;i<3;i++){int pivot=i;for(int j=i+1;j<3;j++)if(Math.abs(a[j][i])>Math.abs(a[pivot][i]))pivot=j;double[] tmp=a[i];a[i]=a[pivot];a[pivot]=tmp;double t=b[i];b[i]=b[pivot];b[pivot]=t;if(Math.abs(a[i][i])<1e-8)return new float[3];double divisor=a[i][i];for(int k=i;k<3;k++)a[i][k]/=divisor;b[i]/=divisor;for(int j=0;j<3;j++)if(j!=i){double f=a[j][i];for(int k=i;k<3;k++)a[j][k]-=f*a[i][k];b[j]-=f*b[i];}}
        return new float[]{(float)b[0],(float)b[1],(float)b[2]};
    }
    private void rescale(float factor){
        if(!Float.isFinite(factor)||factor<.1f||factor>10)return;
        mapX*=factor;mapY*=factor;mapZ*=factor;distance*=factor;speed*=factor;
        for(float[] p:path){p[0]*=factor;p[1]*=factor;p[2]*=factor;}
        for(Map.Entry<Integer,Observation> e:observations.entrySet()){Observation o=e.getValue();e.setValue(new Observation(o.x*factor,o.y*factor,o.z*factor,o.yaw,o.u,o.v));}
        landmarkById.clear();for(int i=0;i<map.size();i++){MapPoint p=map.get(i);MapPoint q=new MapPoint(p.id,p.x*factor,p.y*factor,p.z*factor,p.depth*factor);map.set(i,q);landmarkById.put(q.id,q);}
    }

    void reset(){previous.clear();map.clear();path.clear();observations.clear();mappedIds.clear();oldPixels=null;mapX=mapY=mapZ=yaw=previousGyroYaw=0;previousTimeNs=-1;frames=nextId=0;distance=0;speed=forwardVelocity=0;metricScale=1;scaleInitialized=false;scaleCandidates.clear();landmarkById.clear();}
    private Result snapshot(List<Point> t,int detected){return new Result(t,new ArrayList<>(map),new ArrayList<>(path),detected,mapX,mapY,mapZ,yaw,distance,speed,scaleInitialized,metricScale);}

    /** Triangulate the two camera rays in the estimator's own coordinate frame. */
    private MapPoint triangulate(int id,Observation a,Observation b) {
        float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
        float baseline=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(baseline<.25f)return null;
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
    private Point match(Point p,int[] old,int[] now,int w,int h,float rotation){
        Point q=matchOne(p,old,now,w,h,rotation);if(q==null)return null;
        Point back=matchOne(q,now,old,w,h,-rotation);
        return back!=null&&distance2(back,p)<1.5f? q:null;
    }
    private Point matchOne(Point p,int[] old,int[] now,int w,int h,float rotation){
        if(p.x<12||p.y<12||p.x>=w-12||p.y>=h-12)return null;
        float[] expected=rotatePixel(p.x,p.y,rotation);int cx=Math.round(expected[0]),cy=Math.round(expected[1]),radius=7,side=15;
        float[] patch=new float[49];int k=0;
        for(int py=-3;py<=3;py++)for(int px=-3;px<=3;px++)patch[k++]=bilinear(old,w,p.x+px,p.y+py);
        float best=Float.MAX_VALUE;int bx=0,by=0,bestIndex=0;float[] scores=new float[side*side];
        for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
            int x=cx+dx,y=cy+dy,index=(dy+radius)*side+dx+radius;scores[index]=Float.MAX_VALUE;
            if(x<4||y<4||x>=w-4||y>=h-4)continue;
            float sad=0;k=0;for(int py=-3;py<=3;py++)for(int px=-3;px<=3;px++)sad+=Math.abs(patch[k++]-gray(now[(y+py)*w+x+px]));
            scores[index]=sad;if(sad<best){best=sad;bx=x;by=y;bestIndex=index;}
        }
        if(best>1100)return null;
        float second=Float.MAX_VALUE;for(int i=0;i<scores.length;i++)if(Math.abs(i%side-bestIndex%side)>2||Math.abs(i/side-bestIndex/side)>2)second=Math.min(second,scores[i]);
        if(second<best*1.12f)return null;
        float sx=0,sy=0;if(bestIndex%side>0&&bestIndex%side<side-1)sx=subpixel(scores[bestIndex-1],best,scores[bestIndex+1]);
        if(bestIndex/side>0&&bestIndex/side<side-1)sy=subpixel(scores[bestIndex-side],best,scores[bestIndex+side]);
        return new Point(p.id,bx+sx,by+sy);
    }
    private float subpixel(float left,float center,float right){float den=left+right-2*center;return den>.001f?clamp(.5f*(left-right)/den,-.5f,.5f):0;}
    private float bilinear(int[] p,int w,float x,float y){int ix=(int)x,iy=(int)y;float dx=x-ix,dy=y-iy;return (1-dy)*((1-dx)*gray(p[iy*w+ix])+dx*gray(p[iy*w+ix+1]))+dy*((1-dx)*gray(p[(iy+1)*w+ix])+dx*gray(p[(iy+1)*w+ix+1]));}
    private int gray(int c){return (((c>>16)&255)*3+((c>>8)&255)*6+(c&255))/10;}
    private float median(ArrayList<Float> v){Collections.sort(v);return v.get(v.size()/2);}
    private float distance2(Point a,Point b){float x=a.x-b.x,y=a.y-b.y;return x*x+y*y;}
    private static final class Scored {final int x,y;final float score;Scored(int x,int y,float s){this.x=x;this.y=y;score=s;}}
}
