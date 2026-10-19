package dev.jboehm.vectorvio.gpxar;
import dev.jboehm.vectorvio.gps.GpsRoute;
import dev.jboehm.vectorvio.location.LocationSample;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Offline runner of the unchanged Android estimator classes. No ARCore reprocessing or video decoding. */
public final class GuideReplay {
    private static double d(String s){return Double.parseDouble(s);}
    private static long n(String s){return Long.parseLong(s);}
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("route.tsv events.tsv output.csv");
        List<GpsRoute.Point> points=new ArrayList<>();
        try(BufferedReader in=Files.newBufferedReader(Path.of(args[0]),StandardCharsets.UTF_8)){
            String row;while((row=in.readLine())!=null){String[] c=row.split("\t",-1);points.add(new GpsRoute.Point(d(c[0]),d(c[1]),d(c[2])));}
        }
        ArRouteGuide engine=new ArRouteGuide();engine.setRoute(new GpsRoute("replayed selected route",points));engine.setEnabled(true);
        try(BufferedReader in=Files.newBufferedReader(Path.of(args[1]),StandardCharsets.UTF_8);BufferedWriter out=Files.newBufferedWriter(Path.of(args[2]),StandardCharsets.UTF_8)){
            out.write("receipt_elapsed_ns,camera_elapsed_ns,state,samples,segment,latitude,longitude,allowance_m,residual_m,remaining_m,off_route_m\n");
            String row;long prior=0,lastOutput=0;
            while((row=in.readLine())!=null){
                String[] c=row.split("\t",-1);long ns=n(c[2]);if(ns<prior)throw new IllegalArgumentException("Nonmonotonic replay receipt clock");prior=ns;
                if(c[0].equals("P")){
                    engine.onPose(Integer.parseInt(c[3]),n(c[1]),ns,d(c[4]),d(c[5]));
                    if(lastOutput==0||ns-lastOutput>=1_000_000_000L){
                        ArRouteGuide.Snapshot s=engine.snapshot(ns);lastOutput=ns;
                        out.write(ns+","+c[1]+","+s.state+","+s.samples+","+s.segment+","+GpxArJournal.value(s.latitude)+","+GpxArJournal.value(s.longitude)+","+GpxArJournal.value(s.horizontalAllowanceMeters)+","+GpxArJournal.value(s.residualMeters)+","+GpxArJournal.value(s.remainingMeters)+","+GpxArJournal.value(s.offRouteMeters)+"\n");
                    }
                }else if(c[0].equals("F"))engine.onFix(new LocationSample(LocationSample.Source.valueOf(c[14]),n(c[1]),ns,0,d(c[3]),d(c[4]),d(c[5]),d(c[6]),d(c[7]),d(c[8]),d(c[9]),d(c[10]),d(c[11]),Boolean.parseBoolean(c[12])));
                else throw new IllegalArgumentException("Unknown event kind");
            }
        }
    }
}
