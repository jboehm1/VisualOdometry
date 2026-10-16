package dev.jboehm.vectorvio;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Keeps each session in app-private storage, no GPS, no cloud upload.
 * All disk operations run on one serialized background executor. Export flushes
 * pending samples BEFORE copying the CSV to the user-selected document URI.
 */
public final class CsvRecorder {
    private static final String TAG = "VectorVIO";
    private static final String HEADER =
        "event,wall_time_ms,frame_time_ns,tracking,x_m,y_m,z_m,qx,qy,qz,qw," +
        "path_m,horizontal_m,displacement_m,vertical_m,speed_mps,segments,gaps,outliers\n";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File directory;
    private BufferedWriter writer;
    private volatile File currentFile;
    private volatile String problem;

    public CsvRecorder(Context context) {
        directory = new File(context.getFilesDir(), "sessions");
        if (!directory.exists() && !directory.mkdirs()) {
            problem = "Cannot create session directory";
        }
    }

    public File currentFile() { return currentFile; }
    public String problem() { return problem; }

    public void newSession() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        File target = new File(directory, "vector_vio_" + format.format(new Date()) + ".csv");
        currentFile = target;
        io.execute(() -> {
            try {
                closeWriter();
                writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(target), StandardCharsets.UTF_8));
                writer.write(HEADER);
                writer.flush();
            } catch (Exception e) { failure("Cannot create CSV", e); }
        });
    }

    public void event(String label, long wallTimeMs) {
        String[] columns = new String[19];
        java.util.Arrays.fill(columns, "");
        columns[0] = label;
        columns[1] = Long.toString(wallTimeMs);
        append(String.join(",", columns) + "\n");
    }

    public void pose(long wallMs, long timestampNs, float[] position, float[] q,
                     Odometer.Snapshot s) {
        String row = String.format(Locale.US,
            "POSE,%d,%d,TRACKING,%.6f,%.6f,%.6f,%.7f,%.7f,%.7f,%.7f," +
            "%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d\n",
            wallMs,timestampNs,position[0],position[1],position[2],
            q[0],q[1],q[2],q[3],s.totalMeters,s.horizontalMeters,
            s.displacementMeters,s.verticalMeters,s.speedMps,s.segments,s.gaps,s.outliers);
        append(row);
    }

    private void append(String line) {
        io.execute(() -> {
            try {
                if (writer == null) return;
                writer.write(line);
            } catch (Exception e) { failure("CSV write failed", e); }
        });
    }

    public void export(Context context, File source, Uri destination, Consumer<String> callback) {
        if (source == null) {
            callback.accept("Start a recording before exporting.");
            return;
        }
        io.execute(() -> {
            String status;
            try {
                if (writer != null) writer.flush();
                try (FileInputStream in = new FileInputStream(source);
                     OutputStream out = context.getContentResolver().openOutputStream(destination)) {
                    if (out == null) throw new IllegalStateException("Document provider returned null stream");
                    byte[] block = new byte[32*1024];
                    int n;
                    while ((n=in.read(block))>=0) out.write(block,0,n);
                    out.flush();
                }
                status = "CSV exported successfully";
            } catch (Exception e) {
                failure("Export failed", e);
                status = "Export failed: " + e.getMessage();
            }
            String message=status;
            main.post(() -> callback.accept(message));
        });
    }

    private void closeWriter() throws java.io.IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }

    private void failure(String description, Exception e) {
        problem=description + ": " + e.getMessage();
        Log.e(TAG,description,e);
    }

    public void shutdown() {
        io.execute(() -> {
            try { closeWriter(); } catch (Exception e) { failure("CSV close failed",e); }
        });
        io.shutdown();
    }
}
