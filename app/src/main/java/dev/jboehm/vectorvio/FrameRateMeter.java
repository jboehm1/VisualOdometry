package dev.jboehm.vectorvio;
/** Counts distinct received camera frames over an interval, not the average of inverse gaps. */
final class FrameRateMeter {
    private long last,windowStart;private int intervals;private float hz;
    boolean accept(long ns){
        if(ns<=0||ns<=last)return false;
        if(last==0||ns-last>700_000_000L){windowStart=ns;intervals=0;hz=0;}
        else{
            intervals++;hz=(float)(intervals*1_000_000_000.0/(ns-windowStart));
            if(ns-windowStart>=1_000_000_000L){windowStart=ns;intervals=0;}
        }
        last=ns;return true;
    }
    float hz(){return hz;}
    void reset(){last=windowStart=0;intervals=0;hz=0;}
}
