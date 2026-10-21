package dev.jboehm.vectorvio;
/** Pure world-to-display policy; never alters raw pose/GNSS measurements. */
final class GpxStrokePolicy {
    static float halfWidthMeters(float distance){
        if(!Float.isFinite(distance)||distance<0)return .025f;
        return .022f+.105f/(1f+distance/2.2f);
    }
    static float dpWidth(float distance){
        if(!Float.isFinite(distance)||distance<0)return 1.6f;
        return 1.4f+5.6f/(1f+distance/4f);
    }
    private GpxStrokePolicy(){}
}
