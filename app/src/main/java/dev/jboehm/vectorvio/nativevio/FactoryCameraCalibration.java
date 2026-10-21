package dev.jboehm.vectorvio.nativevio;

import android.content.Context;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.ImageMetadata;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import org.json.JSONArray;
import org.json.JSONObject;

/** SDK-reported factory bootstrap, NEVER a verified camera/IMU calibration.
 * Only camera geometry is carried over; no ARCore trajectory enters Basalt. */
public final class FactoryCameraCalibration {
    private static final String PREFS="native_camera_bootstrap", KEY="factory_v1";
    public final String cameraId;
    public final int width,height,ois,videoStabilization;
    public final float focus;
    public final int[] crop;
    private final double[] parameters;
    public final JSONObject provenance;
    private FactoryCameraCalibration(JSONObject json)throws Exception {
        provenance=json;
        if(!"ARCORE_FACTORY_BOOTSTRAP_UNVERIFIED".equals(json.getString("source"))||json.optBoolean("verified",true)
            ||!android.os.Build.FINGERPRINT.equals(json.getString("device_fingerprint")))throw new IllegalArgumentException("Calibration provenance");
        cameraId=json.getString("camera_id");width=json.getInt("width");height=json.getInt("height");
        focus=(float)json.getDouble("focus_diopters");ois=json.getInt("ois");videoStabilization=json.getInt("video_stabilization");
        JSONArray c=json.getJSONArray("crop_xywh");crop=new int[]{c.getInt(0),c.getInt(1),c.getInt(2),c.getInt(3)};
        JSONArray p=json.getJSONArray("native_parameters");parameters=new double[27];
        if(p.length()!=27||width<32||height<32||width>1920||height>1080||!Float.isFinite(focus)||focus<0)throw new IllegalArgumentException("Unsupported bootstrap");
        for(int i=0;i<27;i++)parameters[i]=p.getDouble(i);
        if(parameters[0]!=width||parameters[1]!=height||crop[2]<=0||crop[3]<=0)throw new IllegalArgumentException("Inconsistent image geometry");
        // Validate geometry without declaring it externally verified.
        double[] transform=new double[16];System.arraycopy(parameters,6,transform,0,16);
        new NativeVioInputGate.Calibration(cameraId,"factory-bootstrap",width,height,0,false,
            parameters[2],parameters[3],parameters[4],parameters[5],transform);
    }
    public double[] parameters(){return parameters.clone();}
    public static FactoryCameraCalibration load(Context context){
        try{return new FactoryCameraCalibration(new JSONObject(context.getSharedPreferences(PREFS,0).getString(KEY,"")));}
        catch(Exception unavailable){return null;}
    }
    /** Throttle at caller. Optional metadata unavailable => retain previous bootstrap. */
    public static void observe(Context context,Session session,Frame frame){
        try {
            if(frame.getCamera().getTrackingState()!=TrackingState.TRACKING||session.getConfig().getFocusMode()!=Config.FocusMode.FIXED)return;
            com.google.ar.core.CameraIntrinsics intrinsics=frame.getCamera().getImageIntrinsics();
            int[] dimensions=intrinsics.getImageDimensions();float[] focal=intrinsics.getFocalLength(),center=intrinsics.getPrincipalPoint();
            ImageMetadata metadata=frame.getImageMetadata();
            float focus=metadata.getFloat(ImageMetadata.LENS_FOCUS_DISTANCE);
            int[] crop=metadata.getIntArray(ImageMetadata.SCALER_CROP_REGION);
            if(crop.length!=4)return;
            float[] columnMajor=new float[16];
            frame.getAndroidSensorPose().inverse().compose(frame.getCamera().getPose()).toMatrix(columnMajor,0);
            double[] p=new double[27];p[0]=dimensions[0];p[1]=dimensions[1];p[2]=focal[0];p[3]=focal[1];p[4]=center[0];p[5]=center[1];
            for(int r=0;r<4;r++)for(int c=0;c<4;c++)p[6+r*4+c]=columnMajor[c*4+r]*(c==1||c==2?-1:1);
            // Upstream generic defaults, not measured sensor noise for this phone.
            p[22]=0.000282;p[23]=0.016;p[24]=0.0001;p[25]=0.001;p[26]=200;
            JSONArray params=new JSONArray();for(double v:p)params.put(v);
            JSONObject json=new JSONObject().put("source","ARCORE_FACTORY_BOOTSTRAP_UNVERIFIED")
                .put("verified",false).put("camera_id",session.getCameraConfig().getCameraId())
                .put("width",dimensions[0]).put("height",dimensions[1]).put("focus_diopters",focus)
                .put("crop_xywh",new JSONArray(crop)).put("ois",metadata.getByte(ImageMetadata.LENS_OPTICAL_STABILIZATION_MODE))
                .put("video_stabilization",metadata.getByte(ImageMetadata.CONTROL_VIDEO_STABILIZATION_MODE))
                .put("native_parameters",params).put("captured_elapsed_ns",frame.getAndroidCameraTimestamp())
                .put("device_fingerprint",android.os.Build.FINGERPRINT)
                .put("camera_imu_time_offset_ns",0).put("time_offset_status","ASSUMED_ZERO_NOT_CALIBRATED")
                .put("noise_status","UPSTREAM_DEFAULT_NOT_MEASURED")
                .put("camera_model","PINHOLE_APPROXIMATION_NO_DISTORTION_CALIBRATION")
                .put("limitation","Factory geometry transfer across APIs is experimental; OIS, distortion, timing and rolling shutter remain unqualified. No ARCore trajectory used by Basalt.");
            new FactoryCameraCalibration(json);
            context.getSharedPreferences(PREFS,0).edit().putString(KEY,json.toString()).apply();
        }catch(Exception unavailable){/* Missing optional metadata must not interrupt baseline. */}
    }
}
