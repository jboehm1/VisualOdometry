package dev.jboehm.vectorvio;

import android.media.Image;
import java.nio.ByteBuffer;

/**
 * Samples the ARCore-provided CPU YUV_420_888 luminance plane at ~1Hz only
 * when available. It never reconfigures the camera, does not retain images and
 * must never block frame rendering or assert a universal lux threshold.
 */
public final class ArCoreFrameLuma {
    public static CameraImageQuality.Metrics measure(Image image){
        if(image==null||image.getWidth()<8||image.getHeight()<8||
            image.getPlanes()==null||image.getPlanes().length<1)return null;
        Image.Plane y=image.getPlanes()[0];
        ByteBuffer bytes=y.getBuffer();
        int stride=y.getRowStride(),pixelStride=y.getPixelStride();
        int width=image.getWidth(),height=image.getHeight();
        if(stride<width||pixelStride<1||bytes==null)return null;
        final int sampleWidth=96,sampleHeight=54;
        int[] gray=new int[sampleWidth*sampleHeight];
        for(int row=0;row<sampleHeight;row++){
            int yy=Math.min(height-1,(int)((row+.5)*height/sampleHeight));
            for(int col=0;col<sampleWidth;col++){
                int xx=Math.min(width-1,(int)((col+.5)*width/sampleWidth));
                long index=(long)yy*stride+(long)xx*pixelStride;
                if(index<0||index>=bytes.limit())return null;
                int q=bytes.get((int)index)&255;
                gray[row*sampleWidth+col]=0xff000000|(q<<16)|(q<<8)|q;
            }
        }
        return CameraImageQuality.analyze(gray,sampleWidth,sampleHeight);
    }
    private ArCoreFrameLuma(){}
}
