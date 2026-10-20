package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpeedAndBikeStatusTest {
    @Test public void cannotShowStaleGpsAsCurrentSpeed(){
        assertTrue(GpsSpeedStatus.evaluate(5001,true,4,true,11,false,0).warning);
        assertTrue(GpsSpeedStatus.evaluate(5001,true,4,true,11,false,0).detail.contains("STALE"));
        assertEquals("— km/h",GpsSpeedStatus.evaluate(0,true,4,false,0,false,0).speedLabel);
    }
    @Test public void reportsMeasuredAccuracyAndSeparatesNetworkFromGps(){
        GpsSpeedStatus.Reading good=GpsSpeedStatus.evaluate(1200,true,5,true,5,true,0.5f);
        assertEquals("18.0 km/h",good.speedLabel);
        assertTrue(good.detail.contains("GPS ±5 m"));
        assertTrue(good.detail.contains("v±1.8"));
        assertFalse(good.warning);
        assertTrue(GpsSpeedStatus.evaluate(0,false,5,true,5,false,0).warning);
        assertTrue(GpsSpeedStatus.evaluate(0,true,60,true,5,false,0).warning);
        assertEquals("— km/h",GpsSpeedStatus.unavailable("GPS OFF").speedLabel);
    }
    @Test public void cameraAngleHintsAreNonAuthoritativeAndOptionalInUi(){
        assertEquals("",BikeViewAdvisor.hint(true,0f,0));
        assertEquals("",BikeViewAdvisor.hint(false,-1.4f,0));
        assertTrue(BikeViewAdvisor.hint(true,-1.4f,0).contains("static ground"));
        assertTrue(BikeViewAdvisor.hint(true,-1.4f,2).contains("moving bike"));
        assertTrue(SettingsCatalog.items(SettingsCatalog.Section.DISPLAY).stream()
            .anyMatch(x->x.action==SettingsCatalog.Action.BIKE_VIEW_HINTS));
    }
}
