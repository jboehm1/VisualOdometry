package dev.jboehm.vectorvio.location;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipFile;

public class LocationComparisonRecorderTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();

    @Test public void rawPairedAndMetadataAreExportedLocally()throws Exception{
        LocationPairer p=new LocationPairer();
        File dir;
        try(LocationComparisonRecorder recorder=new LocationComparisonRecorder(folder.getRoot(),1700000000000L,"S21")){
            dir=recorder.directory();
            LocationSample gps=new LocationSample(LocationSample.Source.GPS_PROVIDER,
                10_000_000_000L,10_010_000_000L,1700000000000L,48,7,3,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,false);
            recorder.record(gps,p.accept(gps),1700000000000L);
            LocationSample fused=new LocationSample(LocationSample.Source.GOOGLE_FUSED,
                10_100_000_000L,10_110_000_000L,1700000000100L,48,7.00005,3,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,false);
            recorder.record(fused,p.accept(fused),1700000000100L);
            recorder.finish("USER_STOP",p.snapshot(),1700000000200L);
            assertEquals(2,recorder.rows());
        }
        String raw=Files.readString(new File(dir,"fixes.csv").toPath());
        assertTrue(raw.contains("GPS_PROVIDER"));assertTrue(raw.contains("GOOGLE_FUSED"));
        assertTrue(Files.readString(new File(dir,"pairs.csv").toPath()).contains("disagreement_m"));
        String metadata=Files.readString(new File(dir,"metadata.json").toPath());
        assertTrue(metadata.contains("NOT_AVAILABLE"));assertTrue(metadata.contains("USER_STOP"));
        File archive=new File(folder.getRoot(),"export.zip");
        try(OutputStream out=new FileOutputStream(archive)){LocationComparisonRecorder.exportZip(dir,out);}
        try(ZipFile zip=new ZipFile(archive)){
            assertEquals(3,zip.size());
            assertNotNull(zip.getEntry("fixes.csv"));
            assertNotNull(zip.getEntry("pairs.csv"));
            assertNotNull(zip.getEntry("metadata.json"));
        }
    }
    @Test public void incompleteSessionCannotMasqueradeAsCompleteZip()throws Exception{
        File dir=folder.newFolder("incomplete");
        try{
            LocationComparisonRecorder.exportZip(dir,new ByteArrayOutputStream());
            fail("Expected missing session file");
        }catch(IOException expected){assertTrue(expected.getMessage().contains("Missing"));}
    }
    @Test public void metadataEscapeKeepsJsonStructure()throws Exception{
        File dir;
        try(LocationComparisonRecorder recorder=new LocationComparisonRecorder(folder.getRoot(),1700000000000L,"S21 \"quoted\"")){
            dir=recorder.directory();
            recorder.finish("USER_STOP",new LocationPairer().snapshot(),1700000000100L);
        }
        String json=Files.readString(new File(dir,"metadata.json").toPath());
        assertTrue(json.contains("S21 \\\"quoted\\\""));
    }
}
