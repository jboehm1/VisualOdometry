package dev.jboehm.vectorvio;

import java.util.Arrays;

/** ISP preview brightness/contrast evidence, NOT sensor lux or proven ARCore tracking. */
public final class CameraImageQuality {
    public static final class Metrics {
        public final int p10,p50,p90;
        public final double darkFraction,clippedFraction,edgeFraction;
        public final int pixels;
        public Metrics(int p10,int p50,int p90,double dark,double clipped,double edges,int pixels) {
            this.p10=p10;this.p50=p50;this.p90=p90;
            darkFraction=dark;clippedFraction=clipped;edgeFraction=edges;this.pixels=pixels;
        }
        public boolean dark(){return p50<48 || p10<9&&darkFraction>.60;}
        public boolean lacksTexture(){return edgeFraction<.045;}
    }
    private CameraImageQuality(){}
    private static int quantile(int[] histogram,int total,int percent){
        int threshold=(int)Math.ceil(total*percent/100.0), cumulative=0;
        for(int i=0;i<histogram.length;i++)if((cumulative+=histogram[i])>=threshold)return i;
        return 255;
    }
    /**
     * Bitmap sampling in PREVIEW 8-bit image space (after ISP tone curve).
     * No absolute lux/SNR can be inferred from this. Use fixed-size 96x54 pixels.
     */
    public static Metrics analyze(int[] argb,int width,int height){
        if(argb==null||width<8||height<8||argb.length!=(long)width*height)
            throw new IllegalArgumentException("Expected valid camera thumbnail");
        int[] luma=new int[argb.length],hist=new int[256];
        int count=0,dark=0,clipped=0,edges=0,edgeSamples=0;
        int x0=width/10,x1=width-width/10,y0=height/10,y1=height-height/10;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int v=argb[y*width+x];
            int r=(v>>>16)&255,g=(v>>>8)&255,b=v&255;
            int q=(54*r+183*g+19*b+128)>>8; // luminance proxy, gamma-compressed
            luma[y*width+x]=q;
            if(x>=x0&&x<x1&&y>=y0&&y<y1){
                hist[q]++;count++;
                if(q<20)dark++;
                if(q>=248)clipped++;
                if(x>x0&&y>y0){
                    // Image gradient = only a very rough spatial-texture indicator.
                    int change=Math.abs(q-luma[y*width+x-1])
                        +Math.abs(q-luma[(y-1)*width+x]);
                    if(change>32)edges++;
                    edgeSamples++;
                }
            }
        }
        return new Metrics(quantile(hist,count,10),quantile(hist,count,50),
            quantile(hist,count,90),(double)dark/count,(double)clipped/count,
            edgeSamples==0?0:(double)edges/edgeSamples,count);
    }
}
