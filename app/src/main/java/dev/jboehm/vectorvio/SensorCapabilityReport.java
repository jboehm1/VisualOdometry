package dev.jboehm.vectorvio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.graphics.ImageFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/**
 * Actual runtime device capability probe, *not* a claim a sensor was measured
 * or that a model has been evaluated. No camera opened, no scan and no location.
 * Not permitted to imply thermography, radar, or depth sensor when absent.
 */
public final class SensorCapabilityReport {
    private SensorCapabilityReport(){}
    private static void put(JSONObject j,String k,Object v) {
        try{j.put(k,v==null?JSONObject.NULL:v);}catch(Exception ignored){}
    }
    public static JSONObject collect(Context context){
        JSONObject root=new JSONObject();
        put(root,"schema",1);
        put(root,"source","ANDROID_RUNTIME_PROBE_NOT_DEVICE_ACCURACY");
        put(root,"phone_model",Build.MODEL);
        put(root,"manufacturer",Build.MANUFACTURER);
        put(root,"android_sdk",Build.VERSION.SDK_INT);
        put(root,"independent_ground_truth",false);
        put(root,"external_radar_or_lwir_supported_by_this_app",false);
        put(root,"privacy","Local-only; sensor vectors may fingerprint a site; exported only by explicit user action.");
        PackageManager pkg=context.getPackageManager();
        JSONObject permissions=new JSONObject();
        put(permissions,"camera_granted",context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED);
        put(permissions,"fine_location_granted",context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
        put(permissions,"activity_recognition_granted",
            Build.VERSION.SDK_INT<29||context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED);
        put(root,"permissions",permissions);
        JSONObject features=new JSONObject();
        put(features,"gps_hardware",pkg.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS));
        put(features,"bluetooth_le",pkg.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE));
        put(features,"wifi",pkg.hasSystemFeature(PackageManager.FEATURE_WIFI));
        put(features,"wifi_rtt_chipset",pkg.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT));
        put(features,"wifi_rtt_requires_ftm_capable_access_point",true);
        put(features,"arcore_depth","UNVERIFIED_WITHOUT_ARCORE_SESSION");
        put(root,"features",features);
        JSONArray sensors=new JSONArray();
        SensorManager sm=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        if(sm!=null)for(Sensor s:sm.getSensorList(Sensor.TYPE_ALL)){
            JSONObject row=new JSONObject();
            put(row,"type",s.getType());
            put(row,"name",s.getName());
            put(row,"vendor",s.getVendor());
            put(row,"resolution",s.getResolution());
            put(row,"max_range",s.getMaximumRange());
            put(row,"min_delay_us",s.getMinDelay());
            put(row,"max_delay_us",s.getMaxDelay());
            put(row,"power_ma",s.getPower());
            put(row,"wake_up",s.isWakeUpSensor());
            put(row,"reporting_mode",s.getReportingMode());
            sensors.put(row);
        }
        put(root,"sensors",sensors);
        JSONArray cams=new JSONArray();
        CameraManager cm=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        if(cm!=null)try{
            for(String id:cm.getCameraIdList()){
                JSONObject row=new JSONObject();
                put(row,"camera_id",id);
                try{
                    CameraCharacteristics ch=cm.getCameraCharacteristics(id);
                    Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
                    put(row,"lens_facing",facing==null?"unknown":facing);
                    Integer hw=ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    put(row,"hardware_level",hw==null?"unknown":hw);
                    Integer clock=ch.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
                    put(row,"timestamp_source",clock==null?"unknown":clock);
                    Range<Long> exposure=ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                    if(exposure!=null){
                        put(row,"exposure_min_ns",exposure.getLower());
                        put(row,"exposure_max_ns",exposure.getUpper());
                    }
                    Range<Integer> iso=ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                    if(iso!=null){
                        put(row,"iso_min",iso.getLower());put(row,"iso_max",iso.getUpper());
                    }
                    android.util.Size sensor=ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    if(sensor!=null){put(row,"pixel_width",sensor.getWidth());put(row,"pixel_height",sensor.getHeight());}
                    StreamConfigurationMap map=ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(map!=null){
                        Size[] outputs=map.getOutputSizes(ImageFormat.YUV_420_888);
                        JSONArray sizes=new JSONArray();
                        if(outputs!=null)for(Size size:outputs)if(size.getWidth()>=320 && size.getHeight()>=240)
                            sizes.put(size.getWidth()+"x"+size.getHeight());
                        put(row,"yuv_output_sizes",sizes);
                    }
                    int[] caps=ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    JSONArray available=new JSONArray();
                    if(caps!=null)for(int capability:caps)available.put(capability);
                    put(row,"camera2_capability_ids",available);
                }catch(SecurityException|CameraAccessException|IllegalArgumentException ex){
                    put(row,"probe_error",ex.getClass().getSimpleName());
                }
                cams.put(row);
            }
        }catch(SecurityException|CameraAccessException ex){
            put(root,"camera_probe_error",ex.getClass().getSimpleName());
        }
        put(root,"cameras",cams);
        return root;
    }
}
