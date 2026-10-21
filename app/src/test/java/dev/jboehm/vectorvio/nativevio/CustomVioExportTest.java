package dev.jboehm.vectorvio.nativevio;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CustomVioExportTest {
    @Test public void exportContainsOnlyThisRunsOpticalSamplesAndRequiresStoppedMetadata()throws Exception {
        File dir=new File(RuntimeEnvironment.getApplication().getCacheDir(),"native-export-"+System.nanoTime());assertTrue(dir.mkdir());
        File meta=new File(dir,"custom_metadata.json");
        Files.write(meta.toPath(),"{\"started_elapsed_ns\":100}".getBytes(StandardCharsets.UTF_8));
        try{CustomVioSession.export(dir,new ByteArrayOutputStream(),"time,quality\n");fail("Unclosed run must not export");}catch(IOException expected){}
        Files.write(meta.toPath(),"{\"started_elapsed_ns\":100,\"finished_elapsed_ns\":200}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir,"session_resources.csv").toPath(),"resources".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        CustomVioSession.export(dir,bytes,"time,quality\n50,BEFORE\n100,FIRST\n150,MIDDLE\n200,LAST\n250,AFTER\n");
        boolean resources=false,optical=false;
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){ZipEntry e;
            while((e=z.getNextEntry())!=null){
                ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=z.read(b))!=-1)data.write(b,0,n);
                if(e.getName().equals("session_resources.csv"))resources=true;
                if(e.getName().equals("optical_quality.csv")){optical=true;assertEquals("time,quality\n100,FIRST\n150,MIDDLE\n200,LAST\n",new String(data.toByteArray(),StandardCharsets.UTF_8));}
            }
        }
        assertTrue(resources&&optical);
    }
}
