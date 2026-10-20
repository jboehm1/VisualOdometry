package dev.jboehm.vectorvio;

/**
 * Deterministic, self-contained, publication-INSPIRED local gradient metric.
 *
 * Based on the publicly readable abstract of Sun et al., Journal of Automation
 * and Intelligence (2025), DOI:10.1016/j.jai.2025.10.001.
 *
 * Not a numerically exact paper reproduction: equations, camera response,
 * weights and constants could not be verified from the full manuscript.
 * Never claim VO accuracy from this photometric proxy alone.
 *
 * Uses 8x6 grid, noise-suppressing 3x3 blur and region-weighted gradients.
 * dQ/dEV via central difference after gamma approximation of ±0.25EV.
 */
public final class RegionWeightedGradient {
    public static final class Result {
        public final double quality,derivativePerEv,sceneIrradianceProxy;
        public final double texturedRegionFraction,clippedFraction;
        public final boolean reliable;
        Result(double q,double dq,double irradiance,double textured,double clipped,boolean reliable){
            quality=q;derivativePerEv=dq;sceneIrradianceProxy=irradiance;
            texturedRegionFraction=textured;clippedFraction=clipped;this.reliable=reliable;
        }
    }
    private static final int COLS=8,ROWS=6;
    private static final double PROBE_EV=.25;
    private RegionWeightedGradient(){}

    public static Result evaluate(int[] argb,int width,int height){
        if(argb==null||width<40||height<30||argb.length!=(long)width*height)
            throw new IllegalArgumentException("Invalid image");
        int n=argb.length;int[] luma=new int[n];
        double linearLight=0;
        for(int i=0;i<n;i++){
            int c=argb[i];
            int y=(54*((c>>>16)&255)+183*((c>>>8)&255)+19*(c&255)+128)>>8;
            luma[i]=y;
            linearLight+=Math.pow(y/255.0,2.2);
        }
        linearLight/=n;
        // Weights from the original image, so the central difference holds
        // region weights constant while changing only exposure level.
        Pass ref=pass(luma,width,height);
        int active=0;double wsum=0;
        for(int i=0;i<ref.grad.length;i++){
            if(ref.grad[i]>=7&&ref.nonClipped[i]>.25){
                active++;
                wsum+=Math.sqrt(ref.grad[i])*ref.nonClipped[i];
            }
        }
        if(active==0||wsum<1) return new Result(0,0,linearLight,0,ref.clipped,false);
        double[] weights=new double[ref.grad.length];
        for(int i=0;i<weights.length;i++){
            if(ref.grad[i]>=7&&ref.nonClipped[i]>.25)
                weights[i]=Math.sqrt(ref.grad[i])*ref.nonClipped[i]/wsum;
        }
        double q=score(ref,weights);
        Pass minus=pass(simulate(luma,-PROBE_EV),width,height);
        Pass plus=pass(simulate(luma,PROBE_EV),width,height);
        double derivative=(score(plus,weights)-score(minus,weights))/(2*PROBE_EV);
        boolean reliable=active>=4&&Double.isFinite(derivative)&&q>=3;
        return new Result(q,derivative,linearLight,(double)active/(ROWS*COLS),
            ref.clipped,reliable);
    }
    private static int[] simulate(int[] src,double ev){
        int[] out=new int[src.length];double gain=Math.pow(2,ev/2.2);
        for(int i=0;i<src.length;i++){
            int y=(int)Math.round(src[i]*gain);
            out[i]=Math.max(0,Math.min(255,y));
        }
        return out;
    }
    private static final class Pass {
        final double[] grad=new double[COLS*ROWS];
        final double[] nonClipped=new double[COLS*ROWS];
        double clipped;
    }
    private static Pass pass(int[] luma,int w,int h){
        int[] smooth=new int[luma.length];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int sum=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
                sum+=luma[(y+dy)*w+x+dx];
            smooth[y*w+x]=(sum+4)/9;
        }
        Pass result=new Pass();int imageClipped=0;
        for(int value:luma)if(value>=250)imageClipped++;
        result.clipped=(double)imageClipped/luma.length;
        for(int ry=0;ry<ROWS;ry++)for(int rx=0;rx<COLS;rx++){
            int id=ry*COLS+rx;
            int x0=Math.max(2,rx*w/COLS),x1=Math.min(w-2,(rx+1)*w/COLS);
            int y0=Math.max(2,ry*h/ROWS),y1=Math.min(h-2,(ry+1)*h/ROWS);
            double gradSum=0;int count=0,valid=0;
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                int index=y*w+x;
                // Suppress one-pixel noisy gradients by blurring first.
                int gx=smooth[index+1]-smooth[index-1];
                int gy=smooth[index+w]-smooth[index-w];
                double g=Math.hypot(gx,gy);
                // Avoid letting one anomalous pixel or noisy high ISO dominate.
                if(g>=3)gradSum+=Math.min(96,g);
                int v=luma[index];
                if(v>=12&&v<=243)valid++;
                count++;
            }
            result.grad[id]=count==0?0:gradSum/count;
            result.nonClipped[id]=count==0?0:(double)valid/count;
        }
        return result;
    }
    private static double score(Pass p,double[] weights){
        double result=0;
        for(int i=0;i<weights.length;i++)
            result+=weights[i]*p.grad[i]*p.nonClipped[i];
        // Mild penalty for blown highlights, even if they are textureless.
        return result*(1-.45*Math.min(1,p.clipped));
    }
}
