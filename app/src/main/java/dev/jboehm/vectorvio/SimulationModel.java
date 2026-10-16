package dev.jboehm.vectorvio;

/** Deterministic indoor walk with ground-truth pose and synthetic phone IMU readings. */
final class SimulationModel {
    static final int SAMPLE_RATE_HZ=60;
    static final long DURATION_NS=40_000_000_000L;
    static final long SEED=0x564543544F52L;

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
        long t=Math.max(0,requestedTimeNs)%DURATION_NS;
        double seconds=t*1e-9;
        double[] pose=pose(seconds);
        double dt=.004;
        double[] before=position(seconds-dt),after=position(seconds+dt);
        double ax=(after[0]-2*pose[0]+before[0])/(dt*dt);
        double ay=(after[1]-2*pose[1]+before[1])/(dt*dt)+9.80665;
        double az=(after[2]-2*pose[2]+before[2])/(dt*dt);
        double[] imu={ax,ay,az,0,0,0};
        double yawBefore=pose(seconds-dt)[3],yawAfter=pose(seconds+dt)[3];
        double yawRate=wrap(yawAfter-yawBefore)/(2*dt);
        double pitchRate=(pose(seconds+dt)[4]-pose(seconds-dt)[4])/(2*dt);
        double rollRate=(pose(seconds+dt)[5]-pose(seconds-dt)[5])/(2*dt);
        // Rotate horizontal specific force into phone axes; gravity remains on phone Y.
        double yaw=pose[3],localX=Math.cos(yaw)*ax-Math.sin(yaw)*az;
        double localZ=Math.sin(yaw)*ax+Math.cos(yaw)*az;
        double noisePhase=seconds*2*Math.PI;
        imu[0]=localX+.035*Math.sin(noisePhase*17.3+1.1)+.018*Math.sin(noisePhase*31.1);
        imu[1]=ay+.045*Math.sin(noisePhase*13.7+.4)+.016*Math.cos(noisePhase*27.2);
        imu[2]=localZ+.032*Math.cos(noisePhase*19.1+.8)+.014*Math.sin(noisePhase*29.7);
        imu[3]=pitchRate+.0035*Math.sin(noisePhase*15.2+.2);
        imu[4]=yawRate+.0030*Math.cos(noisePhase*12.8+.6);
        imu[5]=rollRate+.0040*Math.sin(noisePhase*16.7+1.7);
        return new Sample(t,pose,imu);
    }

    private static double[] pose(double t) {
        double[] p=position(t);
        double dt=.006;
        double[] before=position(t-dt),after=position(t+dt);
        double vx=(after[0]-before[0])/(2*dt),vz=(after[2]-before[2])/(2*dt);
        double yaw=Math.atan2(vx,-vz)+.008*Math.sin(2*Math.PI*.72*t)+.0025*Math.sin(2*Math.PI*3.1*t+.3);
        double pitch=.008*Math.sin(2*Math.PI*.63*t+.4)+.0025*Math.sin(2*Math.PI*2.4*t);
        double roll=.009*Math.sin(2*Math.PI*1.9*t+1.1)+.002*Math.sin(2*Math.PI*2.8*t+.2);
        return new double[]{p[0],p[1],p[2],yaw,pitch,roll};
    }

    private static double[] position(double t) {
        // Follow four straight corridor legs joined by quarter-circle corners. Position and
        // velocity stay continuous at each turn; the image pipeline receives rendered pixels only.
        double side=3.05,radius=.72,straight=2*(side-radius),arc=radius*Math.PI/2,total=4*(straight+arc);
        double duration=DURATION_NS*1e-9,wrapped=t%duration;if(wrapped<0)wrapped+=duration;
        double distance=wrapped/duration*total,segment=straight+arc;
        int quarter=(int)(distance/segment);if(quarter>3)quarter=3;
        double local=distance-quarter*segment;
        double x,z;
        if(local<straight){double u=local;
            if(quarter==0){x=side;z=-side+radius+u;}
            else if(quarter==1){x=side-radius-u;z=side;}
            else if(quarter==2){x=-side;z=side-radius-u;}
            else{x=-side+radius+u;z=-side;}
        }else{double angle=(quarter*Math.PI/2)+(local-straight)/radius;
            double cx=quarter==0?side-radius:quarter==1?-side+radius:quarter==2?-side+radius:side-radius;
            double cz=quarter==0?side-radius:quarter==1?side-radius:quarter==2?-side+radius:-side+radius;
            x=cx+radius*Math.cos(angle);z=cz+radius*Math.sin(angle);
        }
        double gait=2*Math.PI*1.85*t;
        return new double[]{
            x+.0025*Math.sin(2*Math.PI*5.2*t+.4),
            1.42+.009*Math.sin(gait)+.0015*Math.sin(2*Math.PI*4.1*t+1.3),
            z+.002*Math.sin(gait+.8)+.002*Math.cos(2*Math.PI*5.7*t)
        };
    }

    private static double wrap(double angle) {
        while(angle>Math.PI)angle-=2*Math.PI;
        while(angle< -Math.PI)angle+=2*Math.PI;
        return angle;
    }
}
