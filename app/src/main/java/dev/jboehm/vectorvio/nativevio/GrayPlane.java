package dev.jboehm.vectorvio.nativevio;
import java.nio.ByteBuffer;
/** Owned, unrotated grayscale copy; respects Android row/pixel stride and buffer position. */
public final class GrayPlane {
    private GrayPlane(){}
    public static byte[] copy(ByteBuffer buffer,int width,int height,int rowStride,int pixelStride){
        if(width<=0||height<=0||width>4096||height>4096||pixelStride<=0||rowStride<(long)(width-1)*pixelStride+1)
            throw new IllegalArgumentException("Invalid Y plane layout");
        long last=(long)buffer.position()+(long)(height-1)*rowStride+(long)(width-1)*pixelStride;
        if(last>=buffer.limit())throw new IllegalArgumentException("Truncated Y plane");
        byte[] y=new byte[width*height];int offset=buffer.position();
        for(int r=0;r<height;r++)for(int c=0;c<width;c++)y[r*width+c]=buffer.get(offset+r*rowStride+c*pixelStride);
        return y;
    }
}
