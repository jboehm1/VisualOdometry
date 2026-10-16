package dev.jboehm.vectorvio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

/** Versioned bounded map snapshots. Pure Java so persistence and malformed input are testable. */
final class MapArchive {
    static final int MAX_FRAMES=128, MAX_POINTS=240, MAX_IMAGE_BYTES=2_000_000, MAX_POIS=64, MAX_POI_LABEL_CHARS=48;
    static final long MAX_ARCHIVE_BYTES=64L*1024*1024, MAX_IMAGE_TOTAL_BYTES=24L*1024*1024;
    static final int MAGIC=0x56564D33, LEGACY_MAGIC=0x56564D32;
    static final class Keyframe {
        final int segment;
        final long timestamp;
        final float[] pose,points;
        final byte[] image;
        Keyframe(int segment,long timestamp,float[] pose,float[] points,byte[] image) {
            this.segment=segment;this.timestamp=timestamp;this.pose=pose;this.points=points;this.image=image;
        }
    }
    static final class Poi {
        final int segment;
        final long timestamp;
        final String type,label;
        final float[] pose;
        Poi(int segment,long timestamp,String type,String label,float[] pose) {
            this.segment=segment;this.timestamp=timestamp;this.type=type;this.label=label;this.pose=pose;
        }
    }
    final List<Keyframe> frames;
    final List<Poi> pois;
    final boolean manualLoopConstraint;
    MapArchive(List<Keyframe> frames){this(frames,Collections.emptyList(),false);}
    MapArchive(List<Keyframe> frames,boolean manual){this(frames,Collections.emptyList(),manual);}
    MapArchive(List<Keyframe> frames,List<Poi> pois,boolean manual){
        this.frames=Collections.unmodifiableList(new ArrayList<>(frames));
        this.pois=Collections.unmodifiableList(new ArrayList<>(pois));manualLoopConstraint=manual;
    }
    void write(File target) throws IOException {
        try(OutputStream stream=new BufferedOutputStream(new FileOutputStream(target))){writeTo(stream);}
    }
    private void writeTo(OutputStream stream) throws IOException {
        if(frames.size()>MAX_FRAMES)throw new IOException("Too many keyframes");
        if(pois.size()>MAX_POIS)throw new IOException("Too many points of interest");
        long total=13,imageBytes=0;
        DataOutputStream out=new DataOutputStream(stream);
        out.writeInt(MAGIC);out.writeBoolean(manualLoopConstraint);out.writeInt(frames.size());
        for(Keyframe k:frames) {
            if(k.segment<0 || k.timestamp<0 || k.pose.length!=7 || k.points.length%4!=0 || k.points.length>MAX_POINTS*4 || k.image.length>MAX_IMAGE_BYTES)
                throw new IOException("Invalid keyframe size");
            total+=12L+7L*4+4L*k.points.length+4L+k.image.length;
            if(total>MAX_ARCHIVE_BYTES)throw new IOException("Map exceeds 64 MiB");
            imageBytes+=k.image.length;
            if(imageBytes>MAX_IMAGE_TOTAL_BYTES)throw new IOException("Photos exceed 24 MiB map limit");
            double qnorm=0;
            for(int i=0;i<k.pose.length;i++) {
                if(!Float.isFinite(k.pose[i]))throw new IOException("Nonfinite map pose");
                if(i>=3)qnorm+=k.pose[i]*k.pose[i];
            }
            if(qnorm<.25 || qnorm>2.25)throw new IOException("Invalid pose quaternion");
            for(int i=0;i<k.points.length;i++) {
                if(!Float.isFinite(k.points[i]))throw new IOException("Nonfinite map point");
                if(i%4==3 && (k.points[i]<0 || k.points[i]>1))throw new IOException("Invalid point confidence");
            }
            out.writeInt(k.segment);out.writeLong(k.timestamp);
            for(float p:k.pose)out.writeFloat(p);
            out.writeInt(k.points.length);for(float p:k.points)out.writeFloat(p);
            out.writeInt(k.image.length);out.write(k.image);
        }
        out.writeInt(pois.size());
        for(Poi poi:pois) {
            if(poi.segment<0||poi.timestamp<0||poi.pose.length!=7||!validPoiType(poi.type)||poi.label==null||poi.label.trim().isEmpty()||poi.label.length()>MAX_POI_LABEL_CHARS||poi.label.indexOf('\n')>=0||poi.label.indexOf('\r')>=0)
                throw new IOException("Invalid point of interest");
            byte[] type=poi.type.getBytes(StandardCharsets.UTF_8),label=poi.label.getBytes(StandardCharsets.UTF_8);
            if(type.length>24||label.length>MAX_POI_LABEL_CHARS*4)throw new IOException("Point of interest text is too long");
            double qnorm=0;for(int i=0;i<7;i++){if(!Float.isFinite(poi.pose[i]))throw new IOException("Nonfinite point of interest pose");if(i>=3)qnorm+=poi.pose[i]*poi.pose[i];}
            if(qnorm<.25||qnorm>2.25)throw new IOException("Invalid point of interest orientation");
            total+=12+4+type.length+4+label.length+28;if(total>MAX_ARCHIVE_BYTES)throw new IOException("Map exceeds 64 MiB");
            out.writeInt(poi.segment);out.writeLong(poi.timestamp);out.writeUTF(poi.type);out.writeUTF(poi.label);
            for(float v:poi.pose)out.writeFloat(v);
        }
        out.flush();
    }
    static MapArchive read(File source) throws IOException {
        try(InputStream stream=new BufferedInputStream(new FileInputStream(source))){return readFrom(stream);}
    }
    private static MapArchive readFrom(InputStream source) throws IOException {
        // Bound total decoded bytes as well as every individual allocation.
        InputStream limited=new FilterInputStream(source) {
            long remaining=MAX_ARCHIVE_BYTES;
            @Override public int read()throws IOException {
                if(remaining<0)throw new IOException("Map exceeds 64 MiB");
                if(remaining==0){int extra=super.read();if(extra<0)return -1;throw new IOException("Map exceeds 64 MiB");}
                int value=super.read();if(value>=0)remaining--;return value;
            }
            @Override public int read(byte[] bytes,int offset,int length)throws IOException {
                if(length==0)return 0;
                if(remaining==0)return read();
                if(remaining<0)throw new IOException("Map exceeds 64 MiB");
                int count=in.read(bytes,offset,(int)Math.min(length,remaining));if(count>0)remaining-=count;return count;
            }
        };
        DataInputStream in=new DataInputStream(limited);
        int magic=in.readInt();if(magic!=MAGIC&&magic!=LEGACY_MAGIC)throw new IOException("Unsupported map format");
        boolean manual=in.readBoolean();
        int count=bounded(in.readInt(),MAX_FRAMES);List<Keyframe> frames=new ArrayList<>();
        long imageBytes=0;
        for(int i=0;i<count;i++) {
            int segment=in.readInt();long timestamp=in.readLong();
            if(segment<0 || timestamp<0)throw new IOException("Invalid segment or timestamp");
            float[] pose=new float[7];
            for(int j=0;j<7;j++)pose[j]=finite(in.readFloat());
            double qnorm=0;for(int j=3;j<7;j++)qnorm+=pose[j]*pose[j];
            if(qnorm<.25 || qnorm>2.25)throw new IOException("Invalid pose quaternion");
            int length=bounded(in.readInt(),MAX_POINTS*4);
            if(length%4!=0)throw new IOException("Invalid point data");
            float[] points=new float[length];for(int j=0;j<length;j++)points[j]=finite(in.readFloat());
            for(int j=3;j<length;j+=4)if(points[j]<0 || points[j]>1)throw new IOException("Invalid point confidence");
            byte[] image=new byte[bounded(in.readInt(),MAX_IMAGE_BYTES)];in.readFully(image);
            imageBytes+=image.length;if(imageBytes>MAX_IMAGE_TOTAL_BYTES)throw new IOException("Photos exceed 24 MiB map limit");
            frames.add(new Keyframe(segment,timestamp,pose,points,image));
        }
        List<Poi> pois=new ArrayList<>();
        if(magic==MAGIC) {
            int poiCount=bounded(in.readInt(),MAX_POIS);
            for(int i=0;i<poiCount;i++) {
                int segment=in.readInt();long timestamp=in.readLong();String type=in.readUTF(),label=in.readUTF();float[] pose=new float[7];
                if(segment<0||timestamp<0||!validPoiType(type)||label.trim().isEmpty()||label.length()>MAX_POI_LABEL_CHARS||label.indexOf('\n')>=0||label.indexOf('\r')>=0)throw new IOException("Invalid point of interest");
                double qnorm=0;for(int j=0;j<7;j++){pose[j]=finite(in.readFloat());if(j>=3)qnorm+=pose[j]*pose[j];}
                if(qnorm<.25||qnorm>2.25)throw new IOException("Invalid point of interest orientation");
                pois.add(new Poi(segment,timestamp,type,label,pose));
            }
        }
        if(in.read()!=-1)throw new IOException("Unexpected trailing map data");
        return new MapArchive(frames,pois,manual);
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
            for(Poi poi:pois)segments.add(poi.segment);
            put(zip,"README.txt",("VECTOR map snapshot v3 (v2 snapshots remain readable)\nUnits: metres; ARCore Y up; pose quaternion x y z w.\n"+
                "Each segment is independent. Sparse overlapping point samples are not a dense mesh.\n"+
                "points_of_interest.csv contains user-entered anchored labels; these are not automatically recognized objects.\n"+
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
                StringBuilder marks=new StringBuilder("type,label,timestamp_ns,x_m,y_m,z_m\n");
                for(Poi poi:pois)if(poi.segment==segment)marks.append(csv(poi.type)).append(',').append(csv(poi.label)).append(',').append(poi.timestamp).append(',').append(poi.pose[0]).append(',').append(poi.pose[1]).append(',').append(poi.pose[2]).append('\n');
                put(zip,prefix+"points_of_interest.csv",marks.toString().getBytes(StandardCharsets.UTF_8));
            }
            for(int i=0;i<frames.size();i++)if(frames.get(i).image.length>0)
                put(zip,"segment_"+frames.get(i).segment+"/images/keyframe_"+i+".jpg",frames.get(i).image);
        }
    }
    private static void put(ZipOutputStream zip,String name,byte[] bytes)throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
    private static String csv(String value){return "\""+value.replace("\"","\"\"")+"\"";}
    private static boolean validPoiType(String type){return "LANDMARK".equals(type)||"DOOR".equals(type)||"HAZARD".equals(type)||"NOTE".equals(type);}
}
