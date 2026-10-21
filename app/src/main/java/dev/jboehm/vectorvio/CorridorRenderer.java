package dev.jboehm.vectorvio;

/** Calibrated pinhole video generator shared by Android and the executable benchmark.
 * Only this renderer and the evaluator know the scene; SyntheticVision receives RGB + IMU.
 * Ray intersections cover every pixel with a wall, floor or roof. No polygon near-plane holes.
 */
final class CorridorRenderer {
    static final int WIDTH=ReplayCamera.WIDTH,HEIGHT=ReplayCamera.HEIGHT;
    static final float FOCAL=ReplayCamera.FOCAL,CX=ReplayCamera.CX,CY=ReplayCamera.CY;
    private static double rect(double x,double z,double halfX,double halfZ,double radius){
        double qx=Math.abs(x)-(halfX-radius),qz=Math.abs(z)-(halfZ-radius);
        return Math.hypot(Math.max(qx,0),Math.max(qz,0))+Math.min(Math.max(qx,qz),0)-radius;
    }
    static double corridorDistance(double x,double z){return corridorDistance(x,z,SimulationModel.DEFAULT_ROUTE);}
    static double corridorDistance(double x,double z,SimulationModel.Route route){
        double w=route.halfWidth;
        return Math.max(rect(x,z,route.halfX+w,route.halfZ+w,route.radius+w),-rect(x,z,route.halfX-w,route.halfZ-w,route.radius-w));
    }
    static int[] render(SimulationModel.Sample s,float exposure){return render(s,exposure,SimulationModel.DEFAULT_ROUTE);}
    static int[] render(SimulationModel.Sample s,float exposure,SimulationModel.Route route){
        int[] pixels=new int[WIDTH*HEIGHT];
        double co=Math.cos(s.yaw),si=Math.sin(s.yaw);
        for(int x=0;x<WIDTH;x++){
            double u=(x+.5-CX)/FOCAL,dx=co*u-si,dz=-si*u-co,t=0;
            t=traceWall(s.x,s.z,dx,dz,route);
            double wx=s.x+dx*t,wz=s.z+dz*t;
            for(int y=0;y<HEIGHT;y++){
                double dy=-(y+.5-CY)/FOCAL+Math.tan(s.pitch),hitY=s.y+dy*t;
                int color;
                if(hitY>=0&&hitY<=route.height){
                    // Continuous azimuth coordinate: stable texture through curved corners.
                    double along=(Math.atan2(wz,wx)+Math.PI)*5.5;
                    color=texture(along,hitY,0,route.textureSeed);
                }else{
                    boolean roof=hitY>route.height;
                    double plane=roof?route.height:0,planeT=(plane-s.y)/dy;
                    color=texture(s.x+dx*planeT,s.z+dz*planeT,roof?2:1,route.textureSeed);
                }
                int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
                double shade=.68+.32/(1+t*.09);
                int noise=exposure<.2f?((hash(x,y,(int)(s.timeNs/66_666_667L))>>>27)&7):0;
                r=clamp((int)(r*shade*exposure)+noise);g=clamp((int)(g*shade*exposure)+noise);b=clamp((int)(b*shade*exposure)+noise);
                pixels[y*WIDTH+x]=0xff000000|(r<<16)|(g<<8)|b;
            }
        }
        return pixels;
    }
    static double traceWall(double x,double z,double dx,double dz){return traceWall(x,z,dx,dz,SimulationModel.DEFAULT_ROUTE);}
    static double traceWall(double x,double z,double dx,double dz,SimulationModel.Route route){
        double t=0,len=Math.hypot(dx,dz);
        for(int step=0;step<200;step++){
            double distance=-corridorDistance(x+dx*t,z+dz*t,route);
            if(distance<.0002)return t;
            t+=Math.max(.0001,distance/len*.92);
        }
        return t;
    }
    private static int hash(int x,int y,int seed){int h=x*73856093^y*19349663^seed*83492791;h^=h>>>13;h*=1274126177;return h^(h>>>16);}
    private static int texture(double u,double v,int surface,int seed){
        double unit=surface==0?.40:.55;
        int a=(int)Math.floor(u/unit),b=(int)Math.floor(v/unit),h=hash(a,b,surface+seed);
        double fu=u/unit-Math.floor(u/unit),fv=v/unit-Math.floor(v/unit);
        int base=surface==0?125:surface==1?100:155;
        int tone=base+((h>>>24)&63)-31;
        if(fu<.035||fv<.035)tone=45;
        // Small asymmetric wall/floor markings give corners unique local neighborhoods.
        if(fu>.16&&fu<.75&&fv>.18&&fv<.71)tone=55+((h>>>16)&127);
        if(fu>.32&&fu<.57&&fv>.34&&fv<.55)tone=(h&1)==0?224:34;
        int r=clamp(tone+(surface==0?10:0)),g=clamp(tone+10),bl=clamp(tone+(surface==2?17:5));
        return (r<<16)|(g<<8)|bl;
    }
    private static int clamp(int n){return Math.max(0,Math.min(255,n));}
}
