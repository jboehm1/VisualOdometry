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

    @Test public void exportedZipCanBeImportedBackWithPhotos()throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        new MapArchive(Arrays.asList(frame(4,2)),true).exportZip(bytes);
        MapArchive imported=MapArchive.importZip(new ByteArrayInputStream(bytes.toByteArray()));
        assertTrue(imported.manualLoopConstraint);assertEquals(4,imported.frames.get(0).segment);
        assertArrayEquals(new byte[]{1,2,3},imported.frames.get(0).image);
    }
    @Test(expected=IOException.class)public void rejectsUnrelatedZipWithoutExtractingPaths()throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("../../wrong"));zip.write(1);zip.closeEntry();}
        MapArchive.importZip(new ByteArrayInputStream(bytes.toByteArray()));
    }
    @Test(expected=IOException.class)public void rejectsTrailingSnapshotData()throws Exception {
        File file=File.createTempFile("vector-test", ".vmap");
        try {
            new MapArchive(Arrays.asList(frame(1,1))).write(file);
            try(FileOutputStream out=new FileOutputStream(file,true)){out.write(7);}
            MapArchive.read(file);
        } finally {file.delete();}
    }
    @Test(expected=IOException.class)public void refusesToWriteSnapshotsItsReaderWouldReject()throws Exception {
        byte[] image=new byte[MapArchive.MAX_IMAGE_BYTES];
        Arrays.fill(image,(byte)1);
        List<MapArchive.Keyframe> frames=new ArrayList<>();
        for(int i=0;i<13;i++)frames.add(new MapArchive.Keyframe(1,i,new float[]{0,0,0,0,0,0,1},new float[0],image));
        File file=File.createTempFile("vector-test", ".vmap");
        try {new MapArchive(frames).write(file);} finally {file.delete();}
    }
    @Test public void roundTripsPointsOfInterestAndExportsTheirCoordinates()throws Exception {
        MapArchive.Poi poi=new MapArchive.Poi(4,456,"LANDMARK","front door",new float[]{3,1,2,0,0,0,1});
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        new MapArchive(Arrays.asList(frame(4,1)),Arrays.asList(poi),false).exportZip(bytes);
        MapArchive imported=MapArchive.importZip(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1,imported.pois.size());assertEquals("front door",imported.pois.get(0).label);
        assertEquals(3,imported.pois.get(0).pose[0],0);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry entry;boolean found=false;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("segment_4/points_of_interest.csv")) {
                found=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("front door");
            }
            assertTrue(found);
        }
    }
    @Test public void importsLegacyVersionTwoMaps()throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(MapArchive.LEGACY_MAGIC);out.writeBoolean(false);out.writeInt(0);}
        File file=File.createTempFile("vector-v2", ".vmap");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes.toByteArray());}
        try {MapArchive legacy=MapArchive.read(file);assertTrue(legacy.frames.isEmpty());assertTrue(legacy.pois.isEmpty());}
        finally {file.delete();}
    }
}
