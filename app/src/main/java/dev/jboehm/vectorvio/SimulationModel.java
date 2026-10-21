package dev.jboehm.vectorvio;

/** Deterministic indoor walk with ground-truth pose and synthetic phone IMU readings. */
final class SimulationModel {
    static final int SAMPLE_RATE_HZ=60;
    static final long DURATION_NS=40_000_000_000L;
    static final long SEED=0x564543544F52L;
    static final double CORRIDOR_HALF_SIDE=4.0,CENTER_CORNER_RADIUS=1.45,CORRIDOR_HALF_WIDTH=1.15,WALL_HEIGHT=2.85;

    static final class Route {
        final double halfX,halfZ,radius,halfWidth,height;final long durationNs;final boolean reverse;final int textureSeed;
        Route(double halfX,double halfZ,double radius,double halfWidth,double height,long durationNs,boolean reverse,int textureSeed){this.halfX=halfX;this.halfZ=halfZ;this.radius=radius;this.halfWidth=halfWidth;this.height=height;this.durationNs=durationNs;this.reverse=reverse;this.textureSeed=textureSeed;}
    }
    static final Route DEFAULT_ROUTE=new Route(4,4,1.45,1.15,2.85,DURATION_NS,false,11);
    // Held-out geometry, traversal direction, duration/speed and texture; no estimator retuning.
    static final Route REVERSE_VALIDATION_ROUTE=new Route(5.2,3.2,1.2,.95,3.2,50_000_000_000L,true,47);

    static final class Sample {
        final long timeNs;
        final float x,y,z,yaw,pitch,roll;
        final float ax,ay,az,gx,gy,gz;
        Sample(long t,double[] pose,double[] imu) {
            timeNs=t;x=(float)pose[0];y=(float)pose[1];z=(float)pose[2];
            yaw=(float)pose[3];pitch=(float)pose[4];roll=(float)pose[5];
            ax=(float)imu[0];ay=(float)imu[1];az=(float)imu[2];
            gx=(float)imu[3];gy=(float)imu[4];gz=(float)imu[5];
        }
    }

    private SimulationModel() {}

    static Sample sample(long requestedTimeNs) {
        return sample(requestedTimeNs,DEFAULT_ROUTE);
    }
    static Sample sample(long requestedTimeNs,Route route) {
        long t=Math.max(0,requestedTimeNs)%route.durationNs;
        double seconds=t*1e-9;
        double[] pose=pose(seconds,route);
        double dt=.004;
        double[] before=position(seconds-dt,route),after=position(seconds+dt,route);
        double ax=(after[0]-2*pose[0]+before[0])/(dt*dt);
        double ay=(after[1]-2*pose[1]+before[1])/(dt*dt)+9.80665;
        double az=(after[2]-2*pose[2]+before[2])/(dt*dt);
        double[] imu={ax,ay,az,0,0,0};
        double yawBefore=pose(seconds-dt,route)[3],yawAfter=pose(seconds+dt,route)[3];
        double yawRate=wrap(yawAfter-yawBefore)/(2*dt);
        double pitchRate=(pose(seconds+dt,route)[4]-pose(seconds-dt,route)[4])/(2*dt);
        double rollRate=(pose(seconds+dt,route)[5]-pose(seconds-dt,route)[5])/(2*dt);
        // Rotate horizontal specific force into phone axes; gravity remains on phone Y.
        double yaw=pose[3],localX=Math.cos(yaw)*ax-Math.sin(yaw)*az;
        double localZ=Math.sin(yaw)*ax+Math.cos(yaw)*az;
        double noisePhase=seconds*2*Math.PI;
        imu[0]=localX+.010*Math.sin(noisePhase*17.3+1.1)+.006*Math.sin(noisePhase*31.1);
        imu[1]=ay+.014*Math.sin(noisePhase*13.7+.4)+.006*Math.cos(noisePhase*27.2);
        imu[2]=localZ+.009*Math.cos(noisePhase*19.1+.8)+.005*Math.sin(noisePhase*29.7);
        imu[3]=pitchRate+.0010*Math.sin(noisePhase*15.2+.2);
        imu[4]=yawRate+.0009*Math.cos(noisePhase*12.8+.6);
        imu[5]=rollRate+.0011*Math.sin(noisePhase*16.7+1.7);
        return new Sample(Math.max(0,requestedTimeNs),pose,imu);
    }

    private static double[] pose(double t,Route route) {
        double[] p=position(t,route);
        double dt=.006;
        double[] before=position(t-dt,route),after=position(t+dt,route);
        double vx=(after[0]-before[0])/(2*dt),vz=(after[2]-before[2])/(2*dt);
        double yaw=Math.atan2(-vx,-vz)+.0025*Math.sin(2*Math.PI*.7*t)+.0007*Math.sin(2*Math.PI*3.1*t+.3);
        double pitch=.0028*Math.sin(2*Math.PI*.625*t+.4)+.0008*Math.sin(2*Math.PI*2.4*t);
        double roll=.0030*Math.sin(2*Math.PI*1.9*t+1.1)+.0007*Math.sin(2*Math.PI*2.8*t+.2);
        return new double[]{p[0],p[1],p[2],yaw,pitch,roll};
    }

    private static double[] position(double t,Route route) {
        double radius=route.radius,arc=radius*Math.PI/2,total=4*(route.halfX+route.halfZ-2*radius)+4*arc;
        double duration=route.durationNs*1e-9,wrapped=t%duration;if(wrapped<0)wrapped+=duration;
        if(route.reverse)wrapped=(duration-wrapped)%duration;
        double local=wrapped/duration*total;int quarter=0;
        for(;quarter<3;quarter++){double leg=2*((quarter%2==0?route.halfZ:route.halfX)-radius);if(local<leg+arc)break;local-=leg+arc;}
        double straight=2*((quarter%2==0?route.halfZ:route.halfX)-radius),x,z;
        if(local<straight){
            if(quarter==0){x=route.halfX;z=-route.halfZ+radius+local;}
            else if(quarter==1){x=route.halfX-radius-local;z=route.halfZ;}
            else if(quarter==2){x=-route.halfX;z=route.halfZ-radius-local;}
            else{x=-route.halfX+radius+local;z=-route.halfZ;}
        }else{
            double angle=quarter*Math.PI/2+(local-straight)/radius;
            double cx=quarter==0||quarter==3?route.halfX-radius:-route.halfX+radius;
            double cz=quarter<2?route.halfZ-radius:-route.halfZ+radius;
            x=cx+radius*Math.cos(angle);z=cz+radius*Math.sin(angle);
        }
        double gait=2*Math.PI*1.85*t;
        return new double[]{
            x,
            1.42+.0035*Math.sin(gait)+.0005*Math.sin(2*Math.PI*4.1*t+1.3),
            z
        };
    }

    private static double wrap(double angle) {
        while(angle>Math.PI)angle-=2*Math.PI;
        while(angle< -Math.PI)angle+=2*Math.PI;
        return angle;
    }
}
