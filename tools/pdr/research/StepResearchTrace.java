package dev.jboehm.vectorvio.pdr.research;

import dev.jboehm.vectorvio.pdr.PdrEngine;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** SAME deployed baseline engine; challengers only inside this host harness. */
public final class StepResearchTrace {
    public static void main(String[] args) throws Exception {
        PdrEngine current = new PdrEngine();
        PeakCandidate smooth = new PeakCandidate(false), adaptive = new PeakCandidate(true);
        CausalArbitration arbitration=new CausalArbitration();
        List<Long> a = new ArrayList<>(), b = new ArrayList<>(), c = new ArrayList<>(), events=new ArrayList<>(), delays=new ArrayList<>(), combined=new ArrayList<>();
        long previous = -1; int samples = 0;
        try (BufferedReader in = Files.newBufferedReader(Path.of(args[0]))) {
            if (!"t_ns,ax,ay,az".equals(in.readLine())) throw new IllegalArgumentException("schema");
            String line;
            while ((line=in.readLine()) != null) {
                String[] v=line.split(",",-1);
                if(v.length!=4)throw new IllegalArgumentException("sample width");
                long t=Long.parseLong(v[0]);
                double x=Double.parseDouble(v[1]), y=Double.parseDouble(v[2]), z=Double.parseDouble(v[3]);
                if(t<=previous || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                    throw new IllegalArgumentException("invalid sample");
                previous=t;
                if(samples++==0)current.start(t);
                boolean base=current.acceleration(t,x,y,z);
                boolean candidate=adaptive.accept(t,x,y,z);
                if(base)a.add(t);
                if(smooth.accept(t,x,y,z))b.add(t);
                if(candidate) {
                    c.add(t);events.add(adaptive.eventTimestampNs());
                    delays.add(t-adaptive.eventTimestampNs());
                }
                if(arbitration.accept(t,base,candidate))combined.add(t);
            }
        }
        if(samples<500)throw new IllegalArgumentException("too few samples");
        System.out.println("{\"current\":"+a+",\"smooth\":"+b+",\"adaptive\":"+c+
            ",\"adaptive_event_time\":"+events+",\"event_confirmation_delay_ns\":"+delays+
            ",\"consensus\":"+combined+",\"consensus_switches\":"+arbitration.switches()+"}");
    }
}
