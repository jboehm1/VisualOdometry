package dev.jboehm.vectorvio.pdr;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Step timestamp fixture runner. This uses the SAME PdrEngine as Android. */
public final class SimulStepTrace {
    public static void main(String[] args) throws Exception {
        if(args.length != 1)throw new IllegalArgumentException("usage: SimulStepTrace t_ns,ax,ay,az CSV");
        PdrEngine engine=new PdrEngine();
        long previous=Long.MIN_VALUE;
        int events=0;
        try(BufferedReader in=Files.newBufferedReader(Path.of(args[0]))) {
            String head=in.readLine();
            if(!"t_ns,ax,ay,az".equals(head))throw new IllegalArgumentException("bad header");
            String line;
            while((line=in.readLine())!=null){
                String[] values=line.split(",",-1);
                if(values.length!=4)throw new IllegalArgumentException("invalid row");
                long t=Long.parseLong(values[0]);
                double ax=Double.parseDouble(values[1]), ay=Double.parseDouble(values[2]),
                       az=Double.parseDouble(values[3]);
                if(t<=previous || !Double.isFinite(ax) || !Double.isFinite(ay) ||
                   !Double.isFinite(az))throw new IllegalArgumentException("bad IMU");
                previous=t;
                if(events++==0)engine.start(t);
                if(engine.acceleration(t,ax,ay,az))System.out.println(t);
            }
        }
        if(events<50)throw new IllegalArgumentException("incomplete IMU");
    }
}
