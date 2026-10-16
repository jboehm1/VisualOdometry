package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
public class MapArchiveTest {
    private MapArchive.Keyframe frame(int segment,float x) {
        return new MapArchive.Keyframe(segment,123L,new float[]{x,0,0,0,0,0,1},new float[]{x,1,2,.8f},new byte[]{1,2,3});
    }
    @Test public void roundTripsMapPhotosAndManualFlag() throws Exception {
        File file=File.createTempFile("vector-test", ".vmap");
        try {
            new MapArchive(Arrays.asList(frame(1,1),frame(2,2)),true).write(file);
            MapArchive map=MapArchive.read(file);
            assertTrue(map.manualLoopConstraint);assertEquals(2,map.frames.size());
            assertArrayEquals(new byte[]{1,2,3},map.frames.get(0).image);
            assertArrayEquals(new float[]{2,0,0,0,0,0,1},map.frames.get(1).pose,0);
        } finally {file.delete();}
    }
    @Test public void exportSeparatesDisconnectedSegments() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        new MapArchive(Arrays.asList(frame(1,1),frame(2,2))).exportZip(bytes);
        Set<String> names=new HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                names.add(entry.getName());
                if(entry.getName().endsWith("cloud.ply"))assertTrue(new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("element vertex 1"));
            }
        }
        assertTrue(names.contains("segment_1/cloud.ply"));assertTrue(names.contains("segment_2/trajectory.tum"));
        assertTrue(names.contains("segment_2/images/keyframe_1.jpg"));
    }
    @Test(expected=IOException.class) public void rejectsOversizedCountBeforeAllocation() throws Exception {
        File file=File.createTempFile("vector-test", ".vmap");
        try {
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(file))){out.writeInt(MapArchive.MAGIC);out.writeBoolean(false);out.writeInt(Integer.MAX_VALUE);}
            MapArchive.read(file);
        }finally{file.delete();}
    }
    @Test(expected=IOException.class) public void rejectsTruncatedMap() throws Exception {
        File file=File.createTempFile("vector-test", ".vmap");
        try {new MapArchive(Arrays.asList(frame(1,1))).write(file);try(RandomAccessFile out=new RandomAccessFile(file,"rw")){out.setLength(file.length()-1);}MapArchive.read(file);}
        finally{file.delete();}
    }
}
