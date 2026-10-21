package dev.jboehm.vectorvio.pdr;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Independent *test harness* for the same Java PdrEngine used by Android.
 * It adds no algorithm: records exact timestamps when the public
 * PdrEngine.acceleration() method returns an accepted step.
 *
 * Not production runtime code. Never loads ground truth or annotation files.
 */
public final class PdrStepTrace {
    private PdrStepTrace() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("PdrStepTrace <canonical-events.csv> <detected-step-ns.csv>");
        PdrEngine engine = new PdrEngine();
        engine.start(1L);
        long last = 1L;
        int rows = 0;
        try (BufferedReader in = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8);
             java.io.BufferedWriter out = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8)) {
            out.write("step_time_ns\n");
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("t_ns,")) continue;
                String[] f = line.split(",", -1);
                if (f.length != 5)
                    throw new IllegalArgumentException("Wrong replay event columns");
                long t = Long.parseLong(f[0]);
                if (t < last)
                    throw new IllegalArgumentException("Reversed replay event time");
                double x = Double.parseDouble(f[2]), y = Double.parseDouble(f[3]), z = Double.parseDouble(f[4]);
                boolean detected = false;
                switch (f[1]) {
                    case "ACCEL": detected = engine.acceleration(t,x,y,z); break;
                    case "HEADING": engine.deviceHeading(t,x); break;
                    case "STEP": detected = engine.step(t); break;
                    case "PRESSURE": engine.pressure(t,x); break;
                    default: throw new IllegalArgumentException("Unknown replay event " + f[1]);
                }
                if (detected) out.write(t + "\n");
                last=t;
                rows++;
            }
        }
        PdrEngine.Snapshot snapshot = engine.snapshot(last);
        System.out.printf(Locale.US,
            "{\"engine\":\"PdrEngine\",\"rows\":%d,\"steps\":%d,\"distance_m\":%.6f}%n",
            rows, snapshot.steps, snapshot.distanceM);
    }
}
