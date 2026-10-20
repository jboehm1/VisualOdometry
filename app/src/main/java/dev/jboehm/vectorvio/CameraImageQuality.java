package dev.jboehm.vectorvio;

/**
 * Cheap scene diagnostics on downscaled ISP preview. Does NOT measure actual
 * camera noise/SNR, real scene lux, VIO inlier counts or image sharpness truth.
 */
public final class CameraImageQuality {
    public static final class Metrics {
        public final int p10,p50,p90,featureMedian;
        public final double darkFraction,clippedFraction,edgeFraction,coherentEdgeFraction;
        public final int pixels;
        /** Backwards-compatible fixture contract; no inferred rich feature info. */
        public Metrics(int p10,int p50,int p90,double dark,double clipped,double edges,int pixels){
            this(p10,p50,p90,dark,clipped,edges,edges,p50,pixels);
        }
        Metrics(int p10,int p50,int p90,double dark,double clipped,
                double edges,double coherentEdges,int featureMedian,int pixels){
            this.p10=p10;this.p50=p50;this.p90=p90;darkFraction=dark;
            clippedFraction=clipped;edgeFraction=edges;
            coherentEdgeFraction=coherentEdges;this.featureMedian=featureMedian;
            this.pixels=pixels;
        }
        public boolean dark(){return p50<48 || p10<9&&darkFraction>.60;}
        public boolean lacksTexture(){return coherentEdgeFraction<.045;}
    }
    private CameraImageQuality(){}
    private static int quantile(int[] histogram,int total,int percent){
        int threshold=(int)Math.ceil(total*percent/100.0), cumulative=0;
        for(int i=0;i<histogram.length;i++)if((cumulative+=histogram[i])>=threshold)return i;
        return 255;
    }
    /**
     * 96x54 preview in gamma-encoded 8-bit space, center 80% for background
     * rejection. Distinguishes fine 1-pixel high-frequency gradients from
     * 3x3-smoothed, persistent corner/edge candidates. This isn't VIO tracking.
     */
    public static Metrics analyze(int[] argb,int width,int height){
        if(argb==null||width<8||height<8||argb.length!=(long)width*height)
            throw new IllegalArgumentException("Expected valid camera thumbnail");
        int[] luma=new int[argb.length],hist=new int[256],blurred=new int[argb.length];
        int count=0,dark=0,clipped=0,edges=0,edgeSamples=0;
        int x0=width/10,x1=width-width/10,y0=height/10,y1=height-height/10;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int v=argb[y*width+x];
            int r=(v>>>16)&255,g=(v>>>8)&255,b=v&255;
            int q=(54*r+183*g+19*b+128)>>8;
            luma[y*width+x]=q;
            if(x>=x0&&x<x1&&y>=y0&&y<y1){
                hist[q]++;count++;
                if(q<20)dark++;
                if(q>=248)clipped++;
                if(x>x0&&y>y0){
                    int change=Math.abs(q-luma[y*width+x-1])
                        +Math.abs(q-luma[(y-1)*width+x]);
                    if(change>32)edges++;
                    edgeSamples++;
                }
            }
        }
        // 3x3 low-pass before computing spatial gradients suppresses some
        // high-frequency ISP/sensor noise. It cannot separate all noise from
        // genuine detailed texture, nor provide a feature-inlier count.
        for(int y=1;y<height-1;y++)for(int x=1;x<width-1;x++){
            int sum=0;
            for(int ky=-1;ky<=1;ky++)for(int kx=-1;kx<=1;kx++)
                sum+=luma[(y+ky)*width+x+kx];
            blurred[y*width+x]=(sum+4)/9;
        }
        int[] featureHist=new int[256];
        int stableEdges=0,stablePixels=0;
        for(int y=Math.max(2,y0+2);y<Math.min(height-2,y1-2);y++)
            for(int x=Math.max(2,x0+2);x<Math.min(width-2,x1-2);x++){
                int q=luma[y*width+x];
                int index=y*width+x;
                int gradient=Math.abs(blurred[index+1]-blurred[index-1])+
                    Math.abs(blurred[index+width]-blurred[index-width]);
                stablePixels++;
                if(gradient>=22 && q>=12 && q<=243){
                    featureHist[q]++;stableEdges++;
                }
            }
        double coherentFraction=stablePixels==0?0:(double)stableEdges/stablePixels;
        // "Feature median" has no meaning if there are not enough stable
        // texture candidates. Fall back to unweighted scene median.
        int featureMedian=stableEdges>=Math.max(12,stablePixels*.045)?
            quantile(featureHist,stableEdges,50):quantile(hist,count,50);
        return new Metrics(quantile(hist,count,10),quantile(hist,count,50),
            quantile(hist,count,90),(double)dark/count,(double)clipped/count,
            edgeSamples==0?0:(double)edges/edgeSamples,
            coherentFraction,featureMedian,count);
    }
}
