package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
public class CollectionFilesTest {
    @Test public void gzipSensorLogsRoundTripWithoutRoundingAndZipPreservesVideoBytes()throws Exception{
        File dir=Files.createTempDirectory("collect").toFile();
        try{
            String row="123456789012345,1,"+Float.toString(.123456789f)+",-9.81,0.0";
            try(BufferedWriter w=CollectionFiles.log(dir,"imu.csv.gz","time,type,x,y,z")){w.write(row);w.newLine();}
            try(GZIPInputStream in=new GZIPInputStream(new FileInputStream(new File(dir,"imu.csv.gz")))){assertTrue(new String(in.readAllBytes(),StandardCharsets.UTF_8).contains(row));}
            byte[] video={0,1,2,3,4,(byte)255};Files.write(new File(dir,"part0001.mp4").toPath(),video);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();CollectionFiles.zip(dir,bytes);boolean found=false;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null)if(e.getName().equals("part0001.mp4")){assertArrayEquals(video,zip.readAllBytes());found=true;}}
            assertTrue(found);assertTrue(CollectionFiles.isSession("capture_20261017_151000_123"));assertFalse(CollectionFiles.isSession("capture_../bad"));
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
    }
}
