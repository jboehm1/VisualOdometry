package dev.jboehm.vectorvio;

import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Locale;

/** Executes the exact app renderer and estimator without Android. Truth is evaluation-only. */
public final class SimulationBenchmark {
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.US);
        File out=new File(args.length>0?args[0]:"simulation-results");out.mkdirs();
        int loops=args.length>1?Integer.parseInt(args[1]):3;
        String scenario=args.length>2?args[2]:"default";
        SimulationModel.Route route=scenario.equals("reverse")?SimulationModel.REVERSE_VALIDATION_ROUTE:SimulationModel.DEFAULT_ROUTE;
        int fps=args.length>3?Integer.parseInt(args[3]):15,stepsPerLoop=(int)(route.durationNs/1_000_000_000L)*fps;
        float exposure=args.length>4?Float.parseFloat(args[4]):1;
        SyntheticVision vision=new SyntheticVision();
        SimulationModel.Sample start=SimulationModel.sample(0,route),previous=start;
        double c=Math.cos(start.yaw),s=Math.sin(start.yaw),truthDistance=0,estimateDistance=0,lastX=0,lastY=0,lastZ=0;
        double sumError2=0;int frames=0;SyntheticVision.Result result=null;
        try(PrintWriter csv=new PrintWriter(new File(out,"trajectory.csv"));PrintWriter stats=new PrintWriter(new File(out,"loops.csv"))){
            csv.println("time_s,loop,truth_x,truth_y,truth_z,estimated_x,estimated_y,estimated_z,truth_distance_m,estimated_distance_m,truth_speed_m_s,estimated_speed_m_s,tracks,landmarks,position_error_m,metric_initialized,scale");
            stats.println("loop,truth_distance_m,estimated_distance_m,distance_error_pct,closure_error_m,position_rmse_m,landmarks");
            for(int i=0;i<=loops*stepsPerLoop;i++){
                long time=i*1_000_000_000L/fps;SimulationModel.Sample pose=SimulationModel.sample(time,route);
                int[] pixels=CorridorRenderer.render(pose,exposure,route);
                result=vision.process(pixels,CorridorRenderer.WIDTH,CorridorRenderer.HEIGHT,new SyntheticVision.SensorSample(time,pose.ax,pose.ay,pose.az,pose.gx,pose.gy,pose.gz));
                double tx=c*(pose.x-start.x)-s*(pose.z-start.z),tz=s*(pose.x-start.x)+c*(pose.z-start.z),ty=pose.y-start.y;
                double td=i==0?0:norm(pose.x-previous.x,pose.y-previous.y,pose.z-previous.z),ed=i==0?0:norm(result.x-lastX,result.y-lastY,result.z-lastZ);
                truthDistance+=td;estimateDistance=number(result,"distance",estimateDistance+ed);double error=norm(tx-result.x,ty-result.y,tz-result.z);sumError2+=error*error;frames++;
                csv.printf("%.6f,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%.6f,%.0f,%.6f%n",time*1e-9,Math.min(loops,i/stepsPerLoop+1),tx,ty,tz,result.x,result.y,result.z,truthDistance,estimateDistance,td*fps,number(result,"speed",ed*fps),result.trackedCount,result.points.size(),error,number(result,"metricInitialized",0),number(result,"scale",1));
                if(i<=stepsPerLoop&&i%10==0){BufferedImage img=new BufferedImage(CorridorRenderer.WIDTH,CorridorRenderer.HEIGHT,BufferedImage.TYPE_INT_RGB);img.setRGB(0,0,CorridorRenderer.WIDTH,CorridorRenderer.HEIGHT,pixels,0,CorridorRenderer.WIDTH);ImageIO.write(img,"png",new File(out,"camera_"+i+".png"));}
                if(i>0&&i%stepsPerLoop==0){String line=String.format("%d,%.6f,%.6f,%.3f,%.6f,%.6f,%d",i/stepsPerLoop,truthDistance,estimateDistance,100*(estimateDistance/truthDistance-1),norm(result.x,result.y,result.z),Math.sqrt(sumError2/frames),result.points.size());stats.println(line);stats.flush();System.out.println(line);}
                previous=pose;lastX=result.x;lastY=result.y;lastZ=result.z;
            }
        }
        try(PrintWriter path=new PrintWriter(new File(out,"reconstructed_path.csv"))){path.println("x,y,z");for(float[] p:result.path)path.printf("%.6f,%.6f,%.6f%n",p[0],p[1],p[2]);}
        try(PrintWriter config=new PrintWriter(new File(out,"scenario.json"))){config.printf("{\"scenario\":\"%s\",\"half_x\":%.3f,\"half_z\":%.3f,\"radius\":%.3f,\"half_width\":%.3f,\"height\":%.3f,\"duration_s\":%.1f,\"reverse\":%s,\"fps\":%d,\"exposure\":%.3f,\"start_x\":%.6f,\"start_y\":%.6f,\"start_z\":%.6f,\"start_yaw\":%.8f}%n",scenario,route.halfX,route.halfZ,route.radius,route.halfWidth,route.height,route.durationNs*1e-9,route.reverse,fps,exposure,start.x,start.y,start.z,start.yaw);}
        try(PrintWriter points=new PrintWriter(new File(out,"landmarks.csv"))){points.println("id,x,y,z,nearest_truth_surface_m");for(SyntheticVision.MapPoint p:result.points){double wx=start.x+c*p.x+s*p.z,wz=start.z-s*p.x+c*p.z,wy=start.y+p.y;double err=Math.min(Math.abs(CorridorRenderer.corridorDistance(wx,wz,route)),Math.min(Math.abs(wy),Math.abs(wy-route.height)));points.printf("%d,%.6f,%.6f,%.6f,%.6f%n",p.id,p.x,p.y,p.z,err);}}
    }
    private static double number(Object o,String name,double fallback){try{Object value=o.getClass().getDeclaredField(name).get(o);return value instanceof Boolean?((Boolean)value?1:0):((Number)value).doubleValue();}catch(Exception e){return fallback;}}
    private static double norm(double x,double y,double z){return Math.sqrt(x*x+y*y+z*z);}
}
