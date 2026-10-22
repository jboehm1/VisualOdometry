package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.EnumSet;

public final class EngineSwitcherTest {
    @Test public void allEngineSelectionsAreStableAndDistinct(){
        assertEquals(EnumSet.allOf(EngineSwitcher.Engine.class).size(),EngineSwitcher.Engine.values().length);
        assertEquals(3,EngineSwitcher.Engine.values().length);
        assertTrue(EngineSwitcher.Engine.ARCORE.gpxArSupported);
        assertFalse(EngineSwitcher.Engine.BASALT.gpxArSupported);
        assertFalse(EngineSwitcher.Engine.PDR.gpxArSupported);
        for(EngineSwitcher.Engine e:EngineSwitcher.Engine.values())assertFalse(e.label.trim().isEmpty());
    }
    @Test public void activeRecordingCannotJumpToAnotherEstimator(){
        assertFalse(EngineSwitcher.canSwitch(EngineSwitcher.Engine.ARCORE,EngineSwitcher.Engine.BASALT,true));
        assertFalse(EngineSwitcher.canSwitch(EngineSwitcher.Engine.BASALT,EngineSwitcher.Engine.PDR,true));
        assertTrue(EngineSwitcher.canSwitch(EngineSwitcher.Engine.ARCORE,EngineSwitcher.Engine.BASALT,false));
        assertTrue(EngineSwitcher.canSwitch(EngineSwitcher.Engine.BASALT,EngineSwitcher.Engine.ARCORE,false));
        assertFalse(EngineSwitcher.canSwitch(EngineSwitcher.Engine.ARCORE,EngineSwitcher.Engine.ARCORE,false));
    }
}
