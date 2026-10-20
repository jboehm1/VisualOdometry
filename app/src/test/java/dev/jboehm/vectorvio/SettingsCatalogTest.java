package dev.jboehm.vectorvio;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Menu architecture contract: no positional dispatch and every action reachable. */
public class SettingsCatalogTest {
    @Test public void sixCategoriesExposeEverySemanticActionExactlyOnce(){
        assertEquals(6,SettingsCatalog.Section.values().length);
        Set<SettingsCatalog.Action> seen=EnumSet.noneOf(SettingsCatalog.Action.class);
        for(SettingsCatalog.Section section:SettingsCatalog.Section.values()){
            List<SettingsCatalog.Item> items=SettingsCatalog.items(section);
            assertFalse(section.title,items.isEmpty());
            assertEquals(items.size(),SettingsCatalog.count(section));
            for(SettingsCatalog.Item item:items){
                assertEquals(section,item.section);
                assertTrue("Duplicate "+item.action,seen.add(item.action));
                assertFalse(item.title.trim().isEmpty());
                assertFalse(item.description.trim().isEmpty());
                assertEquals(item,SettingsCatalog.byId(item.action));
            }
        }
        assertEquals(EnumSet.allOf(SettingsCatalog.Action.class),seen);
    }
    @Test public void navigationKeepsGpxAndDedicatedManualShiftDiscoverable(){
        List<SettingsCatalog.Item> navigation=SettingsCatalog.items(SettingsCatalog.Section.NAVIGATION);
        assertEquals(SettingsCatalog.Action.GPX_AR,navigation.get(0).action);
        assertEquals(SettingsCatalog.Action.GPS_MANUAL_ALIGN,navigation.get(1).action);
        assertTrue(navigation.stream().anyMatch(x->x.action==SettingsCatalog.Action.RETURN_TRACE));
        assertTrue(navigation.stream().anyMatch(x->x.action==SettingsCatalog.Action.PDR_NO_CAMERA));
        assertEquals(SettingsCatalog.Section.NAVIGATION,SettingsCatalog.byId(SettingsCatalog.Action.PDR_NO_CAMERA).section);
        assertTrue(SettingsCatalog.items(SettingsCatalog.Section.LOCATION).stream()
            .anyMatch(x->x.action==SettingsCatalog.Action.GPS_COMPARISON));
        assertTrue(SettingsCatalog.items(SettingsCatalog.Section.CAPTURE).stream()
            .anyMatch(x->x.action==SettingsCatalog.Action.ARCORE_REPLAY));
    }
    @Test public void descriptionsExplainAccuracyAndPrivacy(){
        assertTrue(SettingsCatalog.byId(SettingsCatalog.Action.GPS_MANUAL_ALIGN).description.contains("manual"));
        assertTrue(SettingsCatalog.byId(SettingsCatalog.Action.GPS_TOGGLE).description.contains("active"));
        assertTrue(SettingsCatalog.byId(SettingsCatalog.Action.VIDEO_TRAINING).description.contains("Opt-in"));
    }
}
