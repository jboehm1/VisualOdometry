package dev.jboehm.vectorvio.gpxar;
/** Presentation only. No flag affects sampling, matching or the saved route. */
public final class RouteDisplayOptions {
    public static final int DIRECTION=1,DISTANCES=2,ENDPOINTS=4,LOCAL_POIS=8,REMAINING=16,ALL=31;
    public final int flags;
    public RouteDisplayOptions(int flags){this.flags=flags&ALL;}
    public boolean enabled(int option){return (flags&option)!=0;}
}
