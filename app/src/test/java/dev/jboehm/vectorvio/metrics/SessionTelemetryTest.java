package dev.jboehm.vectorvio.metrics;

import android.app.Application;
import android.os.SystemClock;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SessionTelemetryTest {
    @Test public void unitsMissingValuesAndCpuDenominatorAreExplicit() {
        assertEquals(200,MetricMath.cpuCorePercent(1_000_000_000L,2000),0);
        assertEquals(2000,MetricMath.batteryPowerMilliwatts(-500000,4000),0);
        assertEquals(-2000,MetricMath.batteryPowerMilliwatts(500000,4000),0);
        assertTrue(Double.isNaN(MetricMath.batteryPowerMilliwatts(Long.MIN_VALUE,4000)));
        assertTrue(Double.isNaN(MetricMath.batteryPowerMilliwatts(Integer.MIN_VALUE,4000)));
        assertEquals("",MetricMath.csv(Double.NaN));assertEquals("",MetricMath.csv(null));
        assertEquals("0",MetricMath.csv(0));
    }
    @Test public void pauseResumeExportAndMissingGpuRemainHonest()throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        File base=new File(app.getCacheDir(),"metrics_"+System.nanoTime());
        SessionTelemetry t=new SessionTelemetry(app,base,"FIXTURE","test",null);
        t.resume();t.frame(100,true);t.frame(100,true);t.frame(200,false);t.frame(300,true);
        t.count("accepted_steps");t.duration("test",2_000_000L);
        t.pause();t.count("accepted_steps");t.frame(400,false);
        t.resume();t.frame(500,true);t.pause();t.close();t.close();
        JSONObject m=new JSONObject(new String(Files.readAllBytes(new File(base+"_resources_metadata.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("RECORDED",m.getString("status"));assertEquals(2,m.getInt("segments"));
        assertEquals(1,m.getJSONObject("counters").getInt("accepted_steps"));
        assertEquals(4,m.getJSONObject("counters").getInt("distinct_camera_frames"));
        assertEquals(1,m.getJSONObject("counters").getInt("duplicate_camera_callbacks"));
        assertEquals(1,m.getJSONObject("counters").getInt("tracking_losses"));
        assertEquals(1,m.getJSONObject("counters").getInt("tracking_recoveries"));
        assertTrue(m.getJSONObject("unavailable").has("gpu_utilization_percent"));
        assertTrue(m.isNull("gpu_utilization_percent"));
        List<String> rows=Files.readAllLines(new File(base+"_resources.csv").toPath());
        assertTrue(rows.size()>=5);assertTrue(rows.get(0).contains("collector_wall_ms"));
        int columns=rows.get(0).split(",",-1).length;
        for(String r:rows)assertEquals(columns,r.split(",",-1).length);
    }
}
