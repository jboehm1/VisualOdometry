package dev.jboehm.vectorvio;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;
public class SessionBundleTest {
    @Test public void bundleKeepsExactSessionAssetsAndNeverMixesOtherSessions()throws Exception{
        Path dir=Files.createTempDirectory("vector-bundle");
        for(String name:new String[]{"vector_vio_123.csv","vector_vio_123_frames.csv","vector_vio_123_gps.csv","vector_vio_123_metadata.json","vector_vio_123_part001.mp4","vector_vio_1234.csv","vector_vio_1234_part001.mp4","secret.txt"})Files.write(dir.resolve(name),name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output=new ByteArrayOutputStream();SessionBundle.write(dir.resolve("vector_vio_123.csv").toFile(),output);
        Set<String> names=new HashSet<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)names.add(entry.getName());}
        assertEquals(6,names.size());assertTrue(names.contains("vector_vio_123_part001.mp4"));assertTrue(names.contains("README.txt"));assertFalse(names.contains("vector_vio_1234.csv"));assertFalse(names.contains("secret.txt"));
        try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
    }
}
