package dev.jboehm.vectorvio.gps;
import java.util.*;
import java.io.*;
/** Synthetic reference-route tests, not road-network recognition or device accuracy. */
public final class GpsBenchmark {
    private static GpsRoute.Point p(double x,double y){return new GpsRoute.Point(Math.toDegrees(y/Geo.R),Math.toDegrees(x/Geo.R),Double.NaN);}
    public static void main(String[] args)throws Exception{
        PrintWriter trace=args.length>0?new PrintWriter(new File(args[0]),"UTF-8"):null;
        if(trace!=null)trace.println("time_s,truth_progress_m,gps_lat,gps_lon,matched_progress_m,matched_lat,matched_lon,status");
        int runs=0,failures=0;double worst=0;long started=System.nanoTime();
        GpsRoute route=new GpsRoute("parallel-loop",Arrays.asList(p(0,0),p(400,0),p(400,25),p(0,25),p(0,0)));
        for(int seed=0;seed<30;seed++){
            Random rng=new Random(seed+20261018);GpsNavigator nav=new GpsNavigator(route);double squares=0;int count=0;
            for(int t=0;t<390;t++){
                double truth=2*t;GpsRoute.Point actual=route.at(truth);double noiseX=rng.nextGaussian()*5,noiseY=rng.nextGaussian()*5;
                // Half of seeds have a persistent 7m bias toward the parallel path.
                if(seed%2==0)noiseY+=7;
                GpsNavigator.Fix f=new GpsNavigator.Fix(actual.lat+Math.toDegrees(noiseY/Geo.R),actual.lon+Math.toDegrees(noiseX/Geo.R),12,2,Double.NaN,Double.NaN,(t+1)*1_000_000_000L);
                nav.update(f,f.ns);
                if(trace!=null&&seed==0){GpsRoute.Point matched=route.at(nav.progress);trace.printf(Locale.US,"%d,%.6f,%.12f,%.12f,%.6f,%.12f,%.12f,%s%n",t,truth,f.lat,f.lon,nav.progress,matched.lat,matched.lon,nav.status);}
                if(t>10){double error=nav.progress-truth;squares+=error*error;count++;}
                if(nav.status==GpsNavigator.Status.ARRIVED&&truth<route.length-30)throw new AssertionError("premature arrival");
            }
            double rmse=Math.sqrt(squares/count);worst=Math.max(worst,rmse);if(rmse>20)failures++;runs++;
        }
        ArrayList<GpsRoute> heldOut=new ArrayList<>();
        heldOut.add(new GpsRoute("figure-eight",Arrays.asList(p(0,0),p(200,200),p(0,200),p(200,0),p(0,0))));
        heldOut.add(new GpsRoute("two-hairpins",Arrays.asList(p(0,0),p(300,0),p(300,40),p(40,40),p(40,100),p(360,100))));
        ArrayList<GpsRoute.Point> zigzag=new ArrayList<>();zigzag.add(p(0,0));for(int i=1;i<=15;i++)zigzag.add(p(40*i,i%2==0?0:55));heldOut.add(new GpsRoute("zigzag",zigzag));
        double heldWorst=0;int heldRuns=0;
        for(GpsRoute shape:heldOut)for(boolean reverse:new boolean[]{false,true})for(int seed=0;seed<10;seed++){
            GpsRoute selected=reverse?shape.reverse():shape;GpsNavigator n=new GpsNavigator(selected);Random rng=new Random(9000+seed);double error=0;int count=0;
            for(int t=0;t*2<selected.length-30;t++){
                double truth=2*t;GpsRoute.Point actual=selected.at(truth);double nx=rng.nextGaussian()*7,ny=rng.nextGaussian()*7;
                GpsNavigator.Fix f=new GpsNavigator.Fix(actual.lat+Math.toDegrees(ny/Geo.R),actual.lon+Math.toDegrees(nx/Geo.R),15,2,Double.NaN,Double.NaN,(t+1)*1_000_000_000L);n.update(f,f.ns);
                if(t>10){double e=n.progress-truth;error+=e*e;count++;}
                if(n.status==GpsNavigator.Status.ARRIVED)throw new AssertionError("held-out early arrival");
            }
            double rmse=Math.sqrt(error/Math.max(1,count));heldWorst=Math.max(heldWorst,rmse);heldRuns++;if(rmse>25){failures++;System.err.println("held-out failure "+shape.name+" reverse="+reverse+" seed="+seed+" rmse="+rmse);}
        }
        ArrayList<GpsRoute.Point> dense=new ArrayList<>();for(int i=0;i<50000;i++)dense.add(p(i,0));GpsRoute large=new GpsRoute("50000 points",dense);GpsNavigator nav=new GpsNavigator(large);long begin=System.nanoTime();
        for(int i=0;i<1000;i++){GpsRoute.Point a=large.at(i*2);GpsNavigator.Fix f=new GpsNavigator.Fix(a.lat,a.lon,5,2,90,Double.NaN,(i+1)*1_000_000_000L);nav.update(f,f.ns);}
        double ms=(System.nanoTime()-begin)/1e6;
        System.out.printf(Locale.US,"{\"scenario\":\"synthetic_parallel_loop_850m\",\"seeds\":%d,\"failures\":%d,\"worst_progress_rmse_m\":%.6f,\"dense_50000_points_1000_fixes_host_ms\":%.3f,\"held_out_runs\":%d,\"held_out_worst_progress_rmse_m\":%.6f,\"total_host_ms\":%.3f}%n",runs,failures,worst,ms,heldRuns,heldWorst,(System.nanoTime()-started)/1e6);
        if(trace!=null){trace.close();if(trace.checkError())throw new IOException("Cannot write benchmark trace");}
        if(failures>0)throw new AssertionError("progress regression");
        if(Math.abs(nav.progress-1998)>10)throw new AssertionError("dense route progress");
    }
}
