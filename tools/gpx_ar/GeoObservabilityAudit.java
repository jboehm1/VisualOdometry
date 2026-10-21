import dev.jboehm.vectorvio.GeoAlignment;
import java.util.Random;
public class GeoObservabilityAudit {
 static double lat(double n){return 48+Math.toDegrees(n/6371008.8);}
 static double lon(double e){return 7+Math.toDegrees(e/(6371008.8*Math.cos(Math.toRadians(48))));}
 public static void main(String[] args){
  int[] accepted=new int[6];long start=System.nanoTime();
  for(int seed=0;seed<500;seed++)for(int scenario=0;scenario<6;scenario++){
   Random rng=new Random(seed);double angle=rng.nextDouble()*2*Math.PI;
   GeoAlignment a=new GeoAlignment();
   for(int i=0;i<(scenario==5?64:scenario>=3?18:10);i++){
    double x=i*(scenario>=3?2:2),z=scenario>=3?Math.sin(i*.5)*4:0;
    double east=33+Math.cos(angle)*x-Math.sin(angle)*z;
    double north=-12+Math.sin(angle)*x+Math.cos(angle)*z;
    if(scenario==0||scenario==1){east=33;north=-12;}
    if(scenario==2){x=i==9?100:i*.1;z=0;east=33+i*.1;north=-12;}
    if(scenario==1){east+=rng.nextGaussian()*.2;north+=rng.nextGaussian()*.2;}
    if(scenario>=3){east+=rng.nextGaussian()*.45;north+=rng.nextGaussian()*.45;}
    if(scenario==5){x=i*18.0/63;z=0;east=33+(i==63?20*Math.cos(angle):0);north=-12+(i==63?20*Math.sin(angle):0);}
    if(scenario==4&&i==8){east+=50;north+=60;}
    a.add(1,(i+1)*1_000_000_000L,x,z,lat(north),lon(east),scenario==5&&i==63?50:3);
   }
   if(a.estimate().state==GeoAlignment.State.CANDIDATE)accepted[scenario]++;
  }
  System.out.printf("{\"cases_per_scenario\":500,\"frozen_gps_candidates\":%d,\"jitter_gps_candidates\":%d,\"outlier_baseline_candidates\":%d,\"coherent_candidates\":%d,\"gps_spike_candidates\":%d,\"uncertain_gps_baseline_candidates\":%d,\"elapsed_ms\":%.3f}%n",accepted[0],accepted[1],accepted[2],accepted[3],accepted[4],accepted[5],(System.nanoTime()-start)/1e6);
 }
}
