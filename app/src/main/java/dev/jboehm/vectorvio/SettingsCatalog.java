package dev.jboehm.vectorvio;

import java.util.*;

/**
 * Stable, presentation-only navigation contract for the Android control center.
 * IDs never depend on ListView index: parallel modules can add actions safely.
 * Nothing here changes sensors, the odometer or user preferences.
 */
final class SettingsCatalog {
    enum Section {
        NAVIGATION("Navigation & GPX","Visual route, imported tracks, return guidance"),
        LOCATION("Location & sensors","GPS, Fused location comparison and device IMU"),
        DISPLAY("Camera & overlays","Visible information, ground and rendering"),
        CAPTURE("Recordings & exports","Record video, collect-only, replay and export"),
        MAPS("3D maps & places","Saved scenes, POIs and mapping settings"),
        ADVANCED("Performance & tests","Energy profiles, simulations and explanations");
        final String title,subtitle;
        Section(String title,String subtitle){this.title=title;this.subtitle=subtitle;}
    }
    enum Action {
        GPX_AR, GPS_MANUAL_ALIGN, GPS_ROUTES, PDR, COURSES, RETURN_TRACE, GPX_EXTENDED_PREVIEW,
        GPS_TOGGLE, GPS_COMPARISON, IMU_TOGGLE,
        LAYERS, UNKNOWN_GROUND, BIKE_VIEW_HINTS, OVERLAY_RENDER,
        VIDEO_TRAINING, COLLECT_ONLY, ARCORE_REPLAY, EXPORT, CAPTURE_INFO,
        MAP_VIEW, MAP_SETTINGS, MAP_SAVED, MAP_PLACE_POI,
        POWER, SIMULATION, HELP
    }
    static final class Item {
        final Section section;final Action action;final String title,description;
        Item(Section section,Action action,String title,String description){
            this.section=section;this.action=action;this.title=title;this.description=description;
        }
    }
    private static final List<Item> ALL=Collections.unmodifiableList(Arrays.asList(
        new Item(Section.NAVIGATION,Action.GPX_AR,"GPX AR — experimental camera guidance","Show an imported route on the observed ground · opt-in"),
        new Item(Section.NAVIGATION,Action.GPX_EXTENDED_PREVIEW,"Extended GPX world preview","Approximate dotted future/past outside ARCore-detected floor; opt-out"),
        new Item(Section.NAVIGATION,Action.GPS_MANUAL_ALIGN,"Adjust GPX on screen","Drag to match real trail · manual, session-only"),
        new Item(Section.NAVIGATION,Action.GPS_ROUTES,"GPS ROUTES — import GPX / follow / record","2D GPS guidance without a camera"),
        new Item(Section.NAVIGATION,Action.PDR,"PDR · NO CAMERA — steps / sensors / GNSS course","Step/IMU walking and running, no camera · local estimate"),
        new Item(Section.NAVIGATION,Action.COURSES,"COURSES — plan manuel hors ligne","Shopping route planner · manual aisles and product places, no visual recognition"),
        new Item(Section.NAVIGATION,Action.RETURN_TRACE,"Return on local trace","Follow the recorded trail back to its start"),

        new Item(Section.LOCATION,Action.GPS_TOGGLE,"GPS speed badge · show / hide","Fresh native GPS vs VIO speed, horizontal ±m and speed ± when reported; updates while this screen is active"),
        new Item(Section.LOCATION,Action.GPS_COMPARISON,"GPS ↔ FUSED — Android location A/B (experimental)","Compare native GPS with Google Fused; correlated sources"),
        new Item(Section.LOCATION,Action.IMU_TOGGLE,"IMU telemetry overlay","Show/hide raw accelerometer and gyroscope"),

        new Item(Section.DISPLAY,Action.LAYERS,"Scene layers","Cycle planes, feature points and trail"),
        new Item(Section.DISPLAY,Action.UNKNOWN_GROUND,"Estimated ground","Optional approximation where no ARCore plane is observed"),
        new Item(Section.DISPLAY,Action.BIKE_VIEW_HINTS,"Bike / downward camera hints","Opt-out view quality warnings · no fake odometry correction"),
        new Item(Section.DISPLAY,Action.OVERLAY_RENDER,"Overlay renderer","Automatic GPU/Canvas and GPU recovery"),

        new Item(Section.CAPTURE,Action.VIDEO_TRAINING,"Training video","Opt-in camera/IMU dataset recording"),
        new Item(Section.CAPTURE,Action.COLLECT_ONLY,"Collect only · screen off","Dedicated capture mode without live mapping"),
        new Item(Section.CAPTURE,Action.ARCORE_REPLAY,"ARCORE REPLAY — evaluate saved video with same odometer","Replay ARCore-recorded MP4 through the real Android pipeline"),
        new Item(Section.CAPTURE,Action.EXPORT,"Export recordings","Current or previous session, CSV or dataset ZIP"),
        new Item(Section.CAPTURE,Action.CAPTURE_INFO,"What gets captured?","Permissions, local files and measurement caveats"),

        new Item(Section.MAPS,Action.MAP_VIEW,"Map controls","Snapshots, loop checks and keyframes"),
        new Item(Section.MAPS,Action.MAP_SAVED,"Saved 3D maps","Open and inspect stored local maps"),
        new Item(Section.MAPS,Action.MAP_SETTINGS,"Mapping thresholds","Keyframe interval, movement and confidence"),
        new Item(Section.MAPS,Action.MAP_PLACE_POI,"Place a POI","Anchor a named point to an observed plane"),

        new Item(Section.ADVANCED,Action.POWER,"Performance profile","Live view refresh rate; AR tracking remains active"),
        new Item(Section.ADVANCED,Action.SIMULATION,"Offline simulation","Synthetic motion; not real-world validation"),
        new Item(Section.ADVANCED,Action.HELP,"Guidance & limitations","Understand accuracy, lock screen and modules")
    ));
    static {
        EnumSet<Action> unique=EnumSet.noneOf(Action.class);
        for(Item item:ALL)if(!unique.add(item.action))
            throw new IllegalStateException("Duplicate settings action "+item.action);
        if(unique.size()!=Action.values().length)throw new IllegalStateException("Unregistered settings action");
        for(Section section:Section.values())if(items(section).isEmpty())
            throw new IllegalStateException("Empty settings section "+section);
    }
    private SettingsCatalog(){}
    static List<Item> items(Section section){
        ArrayList<Item> list=new ArrayList<>();
        for(Item item:ALL)if(item.section==section)list.add(item);
        return Collections.unmodifiableList(list);
    }
    static Item byId(Action id){
        for(Item item:ALL)if(item.action==id)return item;
        throw new IllegalArgumentException("Unknown settings action "+id);
    }
    static int count(Section section){return items(section).size();}
}
