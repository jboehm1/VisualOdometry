package dev.jboehm.vectorvio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
/** Self-contained session export; selects only exact session stem and known sidecars. */
final class SessionBundle {
    static boolean isSessionCsv(String name){return name.matches("vector_vio_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.csv");}
    static String stem(File csv){return csv.getName().replaceFirst("\\.csv$", "");}
    static boolean belongs(String name,String stem){return name.equals(stem+".csv")||(name.startsWith(stem+"_")&&(name.endsWith(".mp4")||name.endsWith(".csv")||name.endsWith(".json")));}
    static int videoParts(File csv){
        String stem=stem(csv);File[] videos=csv.getParentFile().listFiles((dir,name)->belongs(name,stem)&&name.endsWith(".mp4"));
        int count=0;if(videos!=null)for(File f:videos)if(f.length()>0)count++;
        return count;
    }
    static void write(File csv,OutputStream output)throws IOException{
        String stem=stem(csv);File[] files=csv.getParentFile().listFiles((dir,name)->belongs(name,stem));
        if(files==null||!csv.isFile())throw new IOException("Session not found");Arrays.sort(files,Comparator.comparing(File::getName));
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            byte[] buffer=new byte[65536];
            for(File f:files){zip.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();}
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("VECTOR session bundle v1\nMain CSV: poses, diagnostic device-axis IMU and events.\n*_partNNN.mp4: optional ARCore camera + internal sensor dataset, one part per START/RESUME. No microphone audio.\n*_frames.csv: part name, ARCore and CPU image timestamps, tracking, camera intrinsics and ARCore estimated pose.\n*_motion.csv: per-frame world camera position, tracked anchor pose and continuous anchor-relative metric position; reference identifiers change at rebases or tracking discontinuities.\n*_gps.csv: optional device GNSS/network fixes, reported speed and accuracy; independent of local VIO.\n*_gpx_ar_fixes.csv: optional selected GPS/Google Fused observations, including rejected/duplicate pairing inputs; original monotonic timestamps.\n*_gpx_ar_route.csv: selected GPX vertices by route revision, optional original elevation and UNKNOWN vertical datum.\n*_gpx_ar_alignment.csv: experimental alignment state and diagnostic allowances; no absolute accuracy guarantee. Frame timestamp is ARCore time, not a certified GPS timebase.\n*_metadata.json: device and sensor provenance, units and calibration limits.\nAbsence of MP4 means video was not captured; the CSV cannot recreate a video.\nARCore poses are estimates, NOT ground truth. SensorManager IMU and CPU camera clocks are exported separately: camera-IMU offset/extrinsics and rolling shutter must be calibrated before research training.\nPauses/backgrounding stop video; resume creates a new MP4 part. Do not concatenate across gaps without timestamps.\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
    }
}
