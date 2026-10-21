package dev.jboehm.vectorvio.location;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationPairerTest {
    private static final long T=10_000_000_000L;
    private LocationSample fix(LocationSample.Source source,long ns,double lat,double lon,double acc) {
        return new LocationSample(source,ns,ns+20_000_000L,1_700_000_000_000L,lat,lon,acc,
            Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,false);
    }
    @Test public void identicalFixesAreOnlyAgreementNotGroundTruth(){
        LocationPairer p=new LocationPairer();
        assertNull(p.accept(fix(LocationSample.Source.GPS_PROVIDER,T,48,7,3)));
        LocationPairer.Pair pair=p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+100_000_000L,48,7,3));
        assertNotNull(pair);assertEquals(0,pair.distanceMeters,1e-9);
        assertEquals(100_000_000L,pair.absoluteTimestampDeltaNs);assertEquals(1,p.snapshot().pairCount);
    }
    @Test public void disagreementIsPhysicalMetresButNotAbsoluteError(){
        LocationPairer p=new LocationPairer();
        p.accept(fix(LocationSample.Source.GPS_PROVIDER,T,48,7,2));
        LocationPairer.Pair pair=p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+100_000_000L,48,7.0001,2));
        assertNotNull(pair);assertTrue(pair.distanceMeters>7);assertTrue(pair.distanceMeters<9);
        assertEquals(pair.distanceMeters,p.snapshot().meanDisagreementMeters,1e-6);
    }
    @Test public void sameSourceNeverPairsAndDuplicateTimestampRejected(){
        LocationPairer p=new LocationPairer();
        p.accept(fix(LocationSample.Source.GPS_PROVIDER,T,48,7,3));
        assertNull(p.accept(fix(LocationSample.Source.GPS_PROVIDER,T,48,7.1,3)));
        assertNull(p.accept(fix(LocationSample.Source.GPS_PROVIDER,T+1_000_000_000L,48,7.01,3)));
        assertEquals(1,p.snapshot().rejectedGps);assertEquals(0,p.snapshot().pairCount);
    }
    @Test public void staleAndImpreciseFixesArePreservedOnlyInRawLog(){
        LocationPairer p=new LocationPairer();
        LocationSample old=new LocationSample(LocationSample.Source.GPS_PROVIDER,T,T+8_000_000_000L,1,
            48,7,3,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,false);
        assertEquals(LocationSample.Validity.OLD_OR_FUTURE,old.validity());
        p.accept(old);p.accept(fix(LocationSample.Source.GPS_PROVIDER,T+200_000_000L,48,7,60));
        p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+300_000_000L,48,7,3));
        assertEquals(2,p.snapshot().rejectedGps);assertEquals(0,p.snapshot().pairCount);
    }
    @Test public void temporalWindowAndOneToOne(){
        LocationPairer p=new LocationPairer();
        p.accept(fix(LocationSample.Source.GPS_PROVIDER,T,48,7,3));
        assertNull(p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+2_000_000_000L,48,7,3)));
        assertNotNull(p.accept(fix(LocationSample.Source.GPS_PROVIDER,T+2_100_000_000L,48,7,3)));
        assertNull(p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+2_200_000_000L,48,7,3)));
        assertEquals(1,p.snapshot().pairCount);
    }
    @Test public void fifoBoundedAndOldFixDiscarded(){
        LocationPairer p=new LocationPairer();
        for(int i=0;i<20;i++)p.accept(fix(LocationSample.Source.GPS_PROVIDER,T+i*1_000_000_000L,48,7,3));
        assertNotNull(p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+17_000_000_000L,48,7,3)));
        assertEquals(1,p.snapshot().pairCount);
    }
    @Test public void mockFixCannotPair(){
        LocationPairer p=new LocationPairer();
        LocationSample mock=new LocationSample(LocationSample.Source.GPS_PROVIDER,T,T,1,
            48,7,3,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,true);
        p.accept(mock);
        assertNull(p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+100_000_000L,48,7,3)));
        assertEquals(1,p.snapshot().rejectedGps);
    }
    @Test public void resetSeparatesSessions(){
        LocationPairer p=new LocationPairer();
        p.accept(fix(LocationSample.Source.GPS_PROVIDER,T,48,7,3));
        p.reset();
        assertNull(p.accept(fix(LocationSample.Source.GOOGLE_FUSED,T+100_000_000L,48,7,3)));
        assertEquals(0,p.snapshot().gpsCount);
    }
}
