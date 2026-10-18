package dev.jboehm.vectorvio.pdr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Deterministic replay of canonical trace rows: t_ns,type,x,y,z[,bearing_accuracy_deg].
 * Types: START (session time), STEP_LENGTH, HEADING, GPS_COURSE (x rad,
 * y speed, z horizontal accuracy, bearing_accuracy_deg), STEP, ACCEL, PRESSURE.
 * Ground truth and camera tracks MUST NOT be used as engine inputs.
 */
public final class PdrReplay {
    public static void main(String[] args) throws Exception {
        if(args.length<1||args.length>2)throw new IllegalArgumentException("usage: PdrReplay <events.csv> [path-out.csv]");
        PdrEngine p=new PdrEngine();
        boolean started=false;
        long lastTime=1;
        int rows=0,bad=0;
        try(BufferedReader in=Files.newBufferedReader(Paths.get(args[0]),StandardCharsets.UTF_8)){
            String line; while((line=in.readLine())!=null) {
                if(line.isEmpty()||line.startsWith("#")||line.startsWith("t_ns"))continue;
                String[] f=line.split(",",-1);
                if(f.length<5){bad++;continue;}
                try {
                    long t=Long.parseLong(f[0].trim());
                    if(t<=0){bad++;continue;}
                    double x=Double.parseDouble(f[2].trim());
                    double y=Double.parseDouble(f[3].trim());
                    double z=Double.parseDouble(f[4].trim());
                    if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)){bad++;continue;}
                    if(!started) {
                        // START is explicit in newly recorded sessions; older
                        // traces fall back to their first valid event timestamp.
                        p.start(t);started=true;
                    }
                    switch(f[1].trim()) {
                        case "START":break;
                        case "STEP":p.step(t);break;
                        case "ACCEL":p.acceleration(t,x,y,z);break;
                        case "HEADING":p.deviceHeading(t,x);break;
                        case "STEP_LENGTH":p.setStepLengthMeters(x);break;
                        case "GPS_COURSE":
                            if(f.length>=6)
                                p.gpsCourse(t,x,(float)y,(float)z,Float.parseFloat(f[5].trim()));
                            else bad++;
                            break;
                        case "PRESSURE":p.pressure(t,x);break;
                        default:bad++;continue;
                    }
                    rows++;lastTime=Math.max(lastTime,t);
                }catch(NumberFormatException e){bad++;}
            }
        }
        if(!started)throw new IllegalArgumentException("No valid PDR session events");
        PdrEngine.Snapshot s=p.snapshot(lastTime);
        System.out.printf(Locale.US,
            "{\"rows\":%d,\"rejected_rows\":%d,\"steps\":%d,\"distance_m\":%.5f,\"east_m\":%.5f,\"north_m\":%.5f,\"steps_without_heading\":%d}%n",
            rows,bad,s.steps,s.distanceM,s.eastM,s.northM,s.stepsWithoutHeading);
        if(args.length==2) {
            try(BufferedWriter out=Files.newBufferedWriter(Paths.get(args[1]),StandardCharsets.UTF_8)){
                out.write("t_ns,east_m,north_m,gap_before\n");
                for(PdrEngine.Point point:p.path())
                    out.write(String.format(Locale.US,"%d,%.6f,%.6f,%s%n",point.timeNs,point.eastM,point.northM,point.gapBefore));
            }
        }
    }
}
