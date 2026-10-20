package dev.jboehm.vectorvio.vision;

/**
 * Offline, no-ML *appearance similarity* baseline for benchmark comparisons.
 * This is NOT semantic path segmentation and MUST NOT control navigation.
 * RGB pixels are packed 0x00RRGGBB. No images are retained.
 */
public final class TrailAppearanceBaseline {
    public enum State { PROPOSAL, INSUFFICIENT_TEXTURE, INVALID }
    public static final class Output {
        public final State state;public final int width,height;
        public final byte[] proposal;public final double textureVariance,proposalRatio;
        Output(State state,int width,int height,byte[] proposal,double textureVariance,double proposalRatio){
            this.state=state;this.width=width;this.height=height;this.proposal=proposal;
            this.textureVariance=textureVariance;this.proposalRatio=proposalRatio;
        }
    }
    /** 1/4-resolution simple luminance/color appearance proposal relative to bottom center. */
    public static Output evaluate(int[] pixels,int width,int height){
        if(width<16||height<16||width>4096||height>4096||pixels==null||(long)width*height!=pixels.length)
            return new Output(State.INVALID,0,0,new byte[0],0,0);
        int count=0;double sumR=0,sumG=0,sumB=0,sumLum=0,sumLum2=0;
        for(int y=height*3/4;y<height;y+=2)for(int x=width*2/5;x<width*3/5;x+=2){
            int p=pixels[y*width+x],r=(p>>16)&255,g=(p>>8)&255,b=p&255;
            double lum=(r*3+g*6+b)/10.;count++;sumR+=r;sumG+=g;sumB+=b;sumLum+=lum;sumLum2+=lum*lum;
        }
        if(count<4)return new Output(State.INVALID,0,0,new byte[0],0,0);
        double variance=Math.max(0,sumLum2/count-Math.pow(sumLum/count,2));
        int w=width/4,h=height/4;byte[] proposal=new byte[w*h];
        if(variance<12)return new Output(State.INSUFFICIENT_TEXTURE,w,h,proposal,variance,0);
        double r0=sumR/count,g0=sumG/count,b0=sumB/count;int marked=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=pixels[(y*4+2)*width+(x*4+2)],r=(p>>16)&255,g=(p>>8)&255,b=p&255;
            double diff=Math.sqrt((Math.pow(r-r0,2)+Math.pow(g-g0,2)+Math.pow(b-b0,2))/3);
            if(diff<38){proposal[y*w+x]=1;marked++;}
        }
        return new Output(State.PROPOSAL,w,h,proposal,variance,(double)marked/(w*h));
    }
    private TrailAppearanceBaseline(){}
}
