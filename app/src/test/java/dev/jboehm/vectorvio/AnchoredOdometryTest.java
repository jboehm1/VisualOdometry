package dev.jboehm.vectorvio;
import com.google.ar.core.Pose;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnchoredOdometryTest {
    static class Ref implements AnchoredOdometry.Reference {
        Pose p;boolean valid=true,detached;Ref(Pose p){this.p=p;}
        public Pose pose(){return p;}public boolean tracking(){return valid;}public void detach(){detached=true;}
    }
    static class Factory implements AnchoredOdometry.Factory {
        Ref last;int count;boolean fail;
        public AnchoredOdometry.Reference create(Pose p){if(fail)throw new IllegalStateException();count++;return last=new Ref(p);}
    }
    private Pose p(float x){return Pose.makeTranslation(x,0,0);}
    @Test public void rebaseKeepsRealTravelAndDetachesOldReference(){
        Factory f=new Factory();AnchoredOdometry t=new AnchoredOdometry();
        t.update(f,p(0),1_000_000_000L);Ref old=f.last;assertTrue(t.freshReference);
        assertEquals(5,t.update(f,p(5),1_100_000_000L).tx(),1e-6);assertFalse(t.freshReference);assertTrue(old.detached);
        assertEquals(6,t.update(f,p(6),1_200_000_000L).tx(),1e-6);assertEquals(2,f.count);
        t.reset();assertTrue(f.last.detached);
    }
    @Test public void lossAndGapCreateFreshSegmentsInsteadOfBridgingReferences(){
        Factory f=new Factory();AnchoredOdometry t=new AnchoredOdometry();
        t.update(f,p(0),1_000_000_000L);Ref old=f.last;old.valid=false;
        assertEquals(0,t.update(f,p(50),1_100_000_000L).tx(),1e-6);assertTrue(t.freshReference);assertTrue(old.detached);
        t.update(f,p(60),3_000_000_000L);assertTrue(t.freshReference);
    }
    @Test public void duplicateFramesDoNotCreateReferencesAndRebaseFailureKeepsOld(){
        Factory f=new Factory();AnchoredOdometry t=new AnchoredOdometry();
        t.update(f,p(0),1_000_000_000L);Ref old=f.last;
        assertNull(t.update(f,p(20),1_000_000_000L));assertEquals(1,f.count);
        f.fail=true;assertEquals(5,t.update(f,p(5),1_100_000_000L).tx(),1e-6);assertFalse(old.detached);
        f.fail=false;assertEquals(6,t.update(f,p(6),1_200_000_000L).tx(),1e-6);assertTrue(old.detached);
    }
}
