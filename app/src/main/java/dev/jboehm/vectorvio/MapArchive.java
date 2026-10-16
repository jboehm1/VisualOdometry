package dev.jboehm.vectorvio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

/** Versioned bounded map snapshots. Pure Java so persistence and malformed input are testable. */
final class MapArchive {
    static final int MAX_FRAMES=128, MAX_POINTS=240, MAX_IMAGE_BYTES=2_000_000;
    static final int MAGIC=0x56564D32;
    static final class Keyframe {
        final int segment;
        final long timestamp;
        final float[] pose,points;
        final byte[] image;
        Keyframe(int segment,long timestamp,float[] pose,float[] points,byte[] image) {
            this.segment=segment;this.timestamp=timestamp;this.pose=pose;this.points=points;this.image=image;
        }
    }
    final List<Keyframe> frames;
    final boolean manualLoopConstraint;
    MapArchive(List<Keyframe> frames){this(frames,false);}
    MapArchive(List<Keyframe> frames,boolean manual){this.frames=Collections.unmodifiableList(new ArrayList<>(frames));manualLoopConstraint=manual;}
    void write(File target) throws IOException {
        try(OutputStream stream=new BufferedOutputStream(new FileOutputStream(target))){writeTo(stream);}
    }
    private void writeTo(OutputStream stream) throws IOException {
        if(frames.size()>MAX_FRAMES)throw new IOException("Too many keyframes");
        DataOutputStream out=new DataOutputStream(stream);
        out.writeInt(MAGIC);out.writeBoolean(manualLoopConstraint);out.writeInt(frames.size());
        for(Keyframe k:frames) {
            if(k.pose.length!=7 || k.points.length%4!=0 || k.points.length>MAX_POINTS*4 || k.image.length>MAX_IMAGE_BYTES)
                throw new IOException("Invalid keyframe size");
            out.writeInt(k.segment);out.writeLong(k.timestamp);
            for(float p:k.pose)out.writeFloat(p);
            out.writeInt(k.points.length);for(float p:k.points)out.writeFloat(p);
            out.writeInt(k.image.length);out.write(k.image);
        }
        out.flush();
    }
    static MapArchive read(File source) throws IOException {
        try(InputStream stream=new BufferedInputStream(new FileInputStream(source))){return readFrom(stream);}
    }
    private static MapArchive readFrom(InputStream source) throws IOException {
        // Bound total decoded bytes as well as every individual allocation.
        InputStream limited=new FilterInputStream(source) {
            long remaining=64L*1024*1024;
            @Override public int read()throws IOException {if(remaining--<=0)throw new IOException("Map exceeds 64 MiB");return super.read();}
            @Override public int read(byte[] bytes,int offset,int length)throws IOException {
                if(remaining<=0)throw new IOException("Map exceeds 64 MiB");
                int count=in.read(bytes,offset,(int)Math.min(length,remaining));if(count>0)remaining-=count;return count;
            }
        };
        DataInputStream in=new DataInputStream(limited);
        if(in.readInt()!=MAGIC)throw new IOException("Unsupported map format");
        boolean manual=in.readBoolean();
        int count=bounded(in.readInt(),MAX_FRAMES);List<Keyframe> frames=new ArrayList<>();
        for(int i=0;i<count;i++) {
            int segment=in.readInt();long timestamp=in.readLong();float[] pose=new float[7];
            for(int j=0;j<7;j++)pose[j]=finite(in.readFloat());
            int length=bounded(in.readInt(),MAX_POINTS*4);
            if(length%4!=0)throw new IOException("Invalid point data");
            float[] points=new float[length];for(int j=0;j<length;j++)points[j]=finite(in.readFloat());
            byte[] image=new byte[bounded(in.readInt(),MAX_IMAGE_BYTES)];in.readFully(image);
            frames.add(new Keyframe(segment,timestamp,pose,points,image));
        }
        return new MapArchive(frames,manual);
    }
    static MapArchive importZip(InputStream source)throws IOException {
        // Only our own format is accepted. No filesystem extraction or unbounded entry scanning.
        try(ZipInputStream zip=new ZipInputStream(source)) {
            ZipEntry entry=zip.getNextEntry();
            if(entry==null || !"snapshot.vmap".equals(entry.getName()))throw new IOException("Choose a VECTOR map ZIP containing snapshot.vmap as first entry");
            return readFrom(zip);
        }
    }
    private static int bounded(int n,int max) throws IOException {if(n<0||n>max)throw new IOException("Invalid map size");return n;}
    private static float finite(float n) throws IOException {if(!Float.isFinite(n))throw new IOException("Nonfinite map coordinate");return n;}

    /** Per-segment exports never imply a transform between disconnected local worlds. */
    void exportZip(OutputStream destination) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(destination)) {
            zip.putNextEntry(new ZipEntry("snapshot.vmap"));writeTo(zip);zip.closeEntry();
            Set<Integer> segments=new TreeSet<>();for(Keyframe k:frames)segments.add(k.segment);
            put(zip,"README.txt",("VECTOR map snapshot v2\nUnits: metres; ARCore Y up; pose quaternion x y z w.\n"+
                "Each segment is independent. Sparse overlapping point samples are not a dense mesh.\n"+
                "Anchors were resolved at save time. This cannot restore ARCore tracking or its private map.\n"+
                "trajectory.tum is sparse keyframe trajectory, not every odometer sample.\n"+
                "Images are optional unrotated camera keyframes, not a synchronized VIO dataset.\n"+
                "Manual endpoint constraint applied: "+manualLoopConstraint+". This is NOT automatic SLAM loop closure.\n").getBytes(StandardCharsets.UTF_8));
            for(int segment:segments) {
                List<Keyframe> group=new ArrayList<>();int points=0;
                for(Keyframe k:frames)if(k.segment==segment){group.add(k);points+=k.points.length/4;}
                String prefix="segment_"+segment+"/";
                StringBuilder ply=new StringBuilder("ply\nformat ascii 1.0\nelement vertex "+points+"\nproperty float x\nproperty float y\nproperty float z\nproperty float confidence\nend_header\n");
                StringBuilder tum=new StringBuilder("# timestamp tx ty tz qx qy qz qw\n");
                StringBuilder index=new StringBuilder("keyframe_index,sensor_frame_time_ns,image_file\n");
                for(Keyframe k:group) {
                    tum.append(String.format(Locale.US,"%.9f",k.timestamp/1e9));
                    for(float p:k.pose)tum.append(' ').append(p);tum.append('\n');
                    for(int i=0;i<k.points.length;i+=4)ply.append(k.points[i]).append(' ').append(k.points[i+1]).append(' ').append(k.points[i+2]).append(' ').append(k.points[i+3]).append('\n');
                }
                for(int i=0;i<frames.size();i++) {
                    Keyframe k=frames.get(i);if(k.segment!=segment)continue;
                    index.append(i).append(',').append(k.timestamp).append(',');
                    if(k.image.length>0)index.append("images/keyframe_").append(i).append(".jpg");
                    index.append('\n');
                }
                put(zip,prefix+"keyframes.csv",index.toString().getBytes(StandardCharsets.UTF_8));
                put(zip,prefix+"cloud.ply",ply.toString().getBytes(StandardCharsets.UTF_8));
                put(zip,prefix+"trajectory.tum",tum.toString().getBytes(StandardCharsets.UTF_8));
            }
            for(int i=0;i<frames.size();i++)if(frames.get(i).image.length>0)
                put(zip,"segment_"+frames.get(i).segment+"/images/keyframe_"+i+".jpg",frames.get(i).image);
        }
    }
    private static void put(ZipOutputStream zip,String name,byte[] bytes)throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
}
