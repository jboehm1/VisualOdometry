package dev.jboehm.vectorvio;

import java.util.Arrays;

/**
 * Lightweight per-preview 2D patch matches for translation AND rotation
 * image displacement. No monocular metric speed, calibrated depth or VO pose.
 *
 * Robust small-baseline proxy, designed for occasional 160x90 thumbnails;
 * rejects ambiguous, low-texture or highly inconsistent correspondences.
 * This cannot detect flow above its ±14 pixel search range at 2 Hz.
 */
public final class CameraPreviewFlow {
    public static final class Result {
        public final double pixelsPerSecondSample,fullFramePixelsPerSecond;
        public final int candidates,inliers;
        public final boolean reliable;
        public final double medianDx,medianDy;
        Result(double ps,double full,int n,int inliers,boolean ok,double dx,double dy){
            pixelsPerSecondSample=ps;fullFramePixelsPerSecond=full;
            candidates=n;this.inliers=inliers;reliable=ok;medianDx=dx;medianDy=dy;
        }
    }
    private static final int PATCH=2,SEARCH=14;
    private CameraPreviewFlow(){}

    private static int[] luma(int[] argb){
        int[] gray=new int[argb.length];
        for(int i=0;i<argb.length;i++){
            int c=argb[i];
            gray[i]=(54*((c>>>16)&255)+183*((c>>>8)&255)+19*(c&255)+128)>>8;
        }
        return gray;
    }
    private static int deviation(int[] a,int[] b,int w,int x,int y,int dx,int dy){
        // Correlation proxy invariant to average brightness shift.
        int aCenter=a[y*w+x],bCenter=b[(y+dy)*w+x+dx],cost=0;
        for(int yy=-PATCH;yy<=PATCH;yy++)for(int xx=-PATCH;xx<=PATCH;xx++){
            int va=a[(y+yy)*w+x+xx]-aCenter;
            int vb=b[(y+dy+yy)*w+x+dx+xx]-bCenter;
            cost+=Math.abs(va-vb);
        }
        return cost;
    }
    private static double median(double[] a,int count){
        double[] b=Arrays.copyOf(a,count);
        Arrays.sort(b);
        return count%2==1?b[count/2]:(b[count/2-1]+b[count/2])*.5;
    }

    public static Result estimate(int[] previous,int[] current,int w,int h,
                                  long deltaNs,double fullWidthScale){
        if(previous==null||current==null||w<80||h<55||
           previous.length!=(long)w*h||current.length!=(long)w*h||
           deltaNs<=0||!Double.isFinite(fullWidthScale)||fullWidthScale<=0)
            return new Result(0,0,0,0,false,0,0);
        int[] a=luma(previous),b=luma(current);
        double[] dx=new double[72],dy=new double[72];
        int matches=0;
        for(int gy=0;gy<6;gy++)for(int gx=0;gx<10;gx++){
            int x0=(gx+1)*w/12,y0=(gy+1)*h/8;
            int bestX=-1,bestY=-1,bestTexture=0;
            for(int yy=y0-4;yy<=y0+4;yy+=2)for(int xx=x0-5;xx<=x0+5;xx+=2){
                if(xx<SEARCH+PATCH+1||yy<SEARCH+PATCH+1||
                   xx>=w-SEARCH-PATCH-1||yy>=h-SEARCH-PATCH-1)continue;
                int idx=yy*w+xx;
                int ex=Math.abs(a[idx+2]-a[idx-2]);
                int ey=Math.abs(a[idx+2*w]-a[idx-2*w]);
                int corner=Math.min(ex,ey);
                if(corner>bestTexture){bestTexture=corner;bestX=xx;bestY=yy;}
            }
            if(bestTexture<12||bestX<0)continue;
            int bx=0,by=0,min=Integer.MAX_VALUE,second=Integer.MAX_VALUE;
            for(int y=-SEARCH;y<=SEARCH;y++)for(int x=-SEARCH;x<=SEARCH;x++){
                int cost=deviation(a,b,w,bestX,bestY,x,y);
                if(cost<min){
                    second=min;min=cost;bx=x;by=y;
                }else if(Math.abs(x-bx)>2||Math.abs(y-by)>2){
                    if(cost<second)second=cost;
                }
            }
            // Zero-mean 5x5 pixel residuals need to be strongly lower than
            // alternatives. Noise, repetitive gratings and illumination
            // transients should not generate fake high-confidence flow.
            if(min>360||second<=min*1.18+12)continue;
            dx[matches]=bx;dy[matches]=by;matches++;
        }
        if(matches<6)return new Result(0,0,matches,0,false,0,0);
        double medX=median(dx,matches),medY=median(dy,matches);
        double[] speeds=new double[matches];int inliers=0;
        for(int i=0;i<matches;i++){
            if(Math.hypot(dx[i]-medX,dy[i]-medY)<=3){
                speeds[inliers++]=Math.hypot(dx[i],dy[i]);
            }
        }
        if(inliers<6||inliers<.40*matches)
            return new Result(0,0,matches,inliers,false,medX,medY);
        double ps=median(speeds,inliers)*(1e9/deltaNs);
        return new Result(ps,ps*fullWidthScale,matches,inliers,true,medX,medY);
    }

    /** 2px smear on actual camera width, never on the downsampled thumbnail. */
    public static long blurCapNs(Result motion, long hardwareMin,long hardwareMax){
        if(hardwareMin<=0||hardwareMax<hardwareMin)
            throw new IllegalArgumentException("Invalid camera exposure limits");
        if(motion==null||!motion.reliable||motion.fullFramePixelsPerSecond<5)
            return hardwareMax;
        long cap=Math.round(2.0e9/motion.fullFramePixelsPerSecond);
        return Math.max(hardwareMin,Math.min(hardwareMax,cap));
    }
}
