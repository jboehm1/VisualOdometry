package dev.jboehm.vectorvio.nativevio;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONArray;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import android.content.Context;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FactoryCameraCalibrationTest {
    private JSONObject factory()throws Exception {
        double[] p={640,480,500,500,320,240,1,0,0,.02,0,1,0,0,0,0,1,0,0,0,0,1,.000282,.016,.0001,.001,200};
        return new JSONObject().put("source","ARCORE_FACTORY_BOOTSTRAP_UNVERIFIED").put("verified",false)
            .put("device_fingerprint",android.os.Build.FINGERPRINT).put("camera_id","0")
            .put("width",640).put("height",480).put("focus_diopters",0).put("ois",0).put("video_stabilization",0)
            .put("crop_xywh",new JSONArray(new int[]{0,0,4000,3000})).put("native_parameters",new JSONArray(p));
    }
    private FactoryCameraCalibration load(JSONObject json){
        Context ctx=RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("native_camera_bootstrap",0).edit().putString("factory_v1",json.toString()).commit();
        return FactoryCameraCalibration.load(ctx);
    }
    @Test public void factoryParametersRemainUnverifiedAndOwned()throws Exception {
        FactoryCameraCalibration c=load(factory());assertNotNull(c);assertFalse(c.provenance.getBoolean("verified"));
        double[] p=c.parameters();p[2]=42;assertEquals(500,c.parameters()[2],0);assertEquals(640,c.width);
    }
    @Test public void rejectsClaimsOfVerificationOtherDeviceAndInconsistentGeometry()throws Exception {
        assertNull(load(factory().put("verified",true)));
        assertNull(load(factory().put("device_fingerprint","different-device")));
        assertNull(load(factory().put("width",1280)));
    }
}
