package dev.jboehm.vectorvio;

import android.media.Image;
import com.google.ar.core.Camera;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.NotYetAvailableException;
import com.google.ar.core.Pose;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * OPTIONAL GPX-only raw-depth occlusion. Not a terrain model or a GNSS correction.
 * Rejects a short world ribbon segment only when a high-confidence observed
 * depth is significantly in front of its midpoint. Unknown depth is transparent.
 *
 * GL thread only. At most 5 image reads/s and 512 examined segments/read.
 * Existing plane-ground projection is always the baseline, even with Depth ON.
 * Neither odometer poses nor sampling cadence are affected.
 */
final class GpxDepthOcclusion {
    static final long PERIOD_NS=200_000_000L;
    static final int MAX_SEGMENTS=512,MIN_CONFIDENCE=180;
    private long sampledAtNs;
    private int cachedCount=-1;
    private BitSet hidden=new BitSet();

    void reset(){sampledAtNs=0;cachedCount=-1;hidden.clear();}

    static boolean hiddenByDepth(int observedMillimeters,int confidence,double virtualMeters){
        return confidence>=MIN_CONFIDENCE && observedMillimeters>200 &&
            Double.isFinite(virtualMeters) && virtualMeters>0.3 &&
            observedMillimeters*.001+0.35<virtualMeters;
    }

    static int unsigned16(ByteBuffer buffer,int offset){
        if(offset<0||offset+1>=buffer.limit())return 0;
        return (buffer.get(offset)&255)|((buffer.get(offset+1)&255)<<8);
    }

    List<float[]> apply(Frame frame,Camera camera,float[] vp,List<float[]> segments,long nowNs){
        if(segments.isEmpty()){reset();return segments;}
        if(nowNs>sampledAtNs && nowNs-sampledAtNs>=PERIOD_NS){
            sampledAtNs=nowNs;
            // The two images must be from the same frame and are always closed.
            try(Image depth=frame.acquireRawDepthImage16Bits();
                Image confidence=frame.acquireRawDepthConfidenceImage()){
                if(depth.getWidth()!=confidence.getWidth() || depth.getHeight()!=confidence.getHeight())
                    return segments;
                BitSet mask=new BitSet(segments.size());
                Pose cameraFromWorld=camera.getPose().inverse();
                Image.Plane dp=depth.getPlanes()[0],cp=confidence.getPlanes()[0];
                ByteBuffer db=dp.getBuffer(),cb=cp.getBuffer();
                for(int i=0;i<Math.min(MAX_SEGMENTS,segments.size());i++){
                    float[] e=segments.get(i);
                    if(e.length!=6)continue;
                    float x=(e[0]+e[3])*.5f,y=(e[1]+e[4])*.5f,z=(e[2]+e[5])*.5f;
                    float[] uv=Projection.point(vp,x,y,z);
                    if(uv==null)continue;
                    float[] texture=new float[2];
                    frame.transformCoordinates2d(Coordinates2d.VIEW_NORMALIZED,uv,
                        Coordinates2d.TEXTURE_NORMALIZED,texture);
                    if(!Float.isFinite(texture[0])||!Float.isFinite(texture[1]) ||
                       texture[0]<0||texture[0]>=1||texture[1]<0||texture[1]>=1)continue;
                    int px=(int)(texture[0]*depth.getWidth()),py=(int)(texture[1]*depth.getHeight());
                    int dOff=py*dp.getRowStride()+px*dp.getPixelStride();
                    int cOff=py*cp.getRowStride()+px*cp.getPixelStride();
                    if(cOff<0||cOff>=cb.limit())continue;
                    int mm=unsigned16(db,dOff),score=cb.get(cOff)&255;
                    float[] local=cameraFromWorld.transformPoint(new float[]{x,y,z});
                    if(hiddenByDepth(mm,score,-local[2]))mask.set(i);
                }
                hidden=mask;cachedCount=segments.size();
            }catch(NotYetAvailableException|IllegalStateException unavailable){
                // Depth not available, unsupported, or paused: draw the original
                // correctly anchored plane ribbon, never invent occlusion.
                reset();return segments;
            }catch(RuntimeException error){reset();return segments;}
        }
        if(cachedCount!=segments.size()||hidden.isEmpty())return segments;
        List<float[]> visible=new ArrayList<>(segments.size()-hidden.cardinality());
        for(int i=0;i<segments.size();i++)if(!hidden.get(i))visible.add(segments.get(i));
        return visible;
    }
}
