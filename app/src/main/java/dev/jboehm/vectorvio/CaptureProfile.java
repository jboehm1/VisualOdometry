package dev.jboehm.vectorvio;
/** Explicit capture tradeoffs. No implicit screen-off change to data quality. */
final class CaptureProfile {
    static final CaptureProfile FULL=new CaptureProfile("full",1280,720,30,3_000_000);
    static final CaptureProfile LIGHT=new CaptureProfile("light",1280,720,15,1_500_000);
    static final CaptureProfile DETAIL=new CaptureProfile("detail",1920,1080,30,6_000_000);
    final String id;final int width,height,fps,bitrate;
    final int imuPeriodUs=5000,gpsPeriodMs=1000;
    private CaptureProfile(String id,int w,int h,int fps,int bitrate){this.id=id;width=w;height=h;this.fps=fps;this.bitrate=bitrate;}
    static CaptureProfile from(String id){return "light".equals(id)?LIGHT:"detail".equals(id)?DETAIL:FULL;}
    int batchingUs(int fifoEvents){return fifoEvents>0?Math.min(250_000,Math.max(0,(fifoEvents/2)*imuPeriodUs)):0;}
}
