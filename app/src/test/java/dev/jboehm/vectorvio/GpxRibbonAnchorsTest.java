package dev.jboehm.vectorvio;
import com.google.ar.core.Pose;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class GpxRibbonAnchorsTest {
    static class Ref implements GpxRibbonAnchors.Reference {
        Pose p;boolean valid=true,detached;Ref(Pose p){this.p=p;}
        public Pose pose(){return p;}public boolean tracking(){return valid;}public void detach(){detached=true;}
    }
    static class Factory implements GpxRibbonAnchors.Factory {
        List<Ref> refs=new ArrayList<>();boolean fail;
        public GpxRibbonAnchors.Reference create(Pose p){if(fail)throw new IllegalStateException();Ref r=new Ref(p);refs.add(r);return r;}
    }
    private List<float[]> edge(float x){return Collections.singletonList(new float[]{x,0,1,x+.5f,0,1});}
    @Test public void localGeometryFollowsAnchorWorldCorrectionAndReusesReference(){
        Factory f=new Factory();GpxRibbonAnchors c=new GpxRibbonAnchors();
        List<float[]> a=c.update(f,Pose.IDENTITY,edge(1),1,1,1,0,0);assertEquals(1,a.size());Ref r=f.refs.get(0);
        r.p=Pose.makeTranslation(r.p.tx()+2,r.p.ty(),r.p.tz());
        List<float[]> b=c.update(f,Pose.IDENTITY,edge(1),1,1,2,0,0);
        assertEquals(3,b.get(0)[0],1e-5);assertEquals(1,f.refs.size());
        List<float[]> corrected=c.update(f,Pose.IDENTITY,edge(1),1,2,3,0,0);
        assertEquals(1,corrected.get(0)[0],1e-5);assertEquals(1,f.refs.size());
        c.clear();assertTrue(r.detached);
    }
    @Test public void creationBoundAndCleanupHoldAcrossLongTravel(){
        Factory f=new Factory();GpxRibbonAnchors c=new GpxRibbonAnchors();List<float[]> edges=new ArrayList<>();
        for(int i=0;i<100;i++)edges.addAll(edge(i*6));
        c.update(f,Pose.IDENTITY,edges,1,1,1,0,0);assertTrue(f.refs.size()<=GpxRibbonAnchors.CREATIONS_PER_FRAME);
        for(int frame=2;frame<30;frame++)c.update(f,Pose.IDENTITY,edges,1,1,frame,0,0);
        assertTrue(c.size()<=GpxRibbonAnchors.LIMIT);
        c.update(f,Pose.IDENTITY,edge(1000),2,1,31,1000,0);assertTrue(f.refs.get(0).detached);
        c.update(f,Pose.IDENTITY,Collections.emptyList(),2,1,32,1000,0);assertEquals(0,c.size());
    }
    @Test public void failureAndLostTrackingDoNotFallBackToUnanchoredRibbon(){
        Factory f=new Factory();GpxRibbonAnchors c=new GpxRibbonAnchors();f.fail=true;
        assertTrue(c.update(f,Pose.IDENTITY,edge(1),1,1,1,0,0).isEmpty());f.fail=false;
        assertTrue(c.update(f,Pose.IDENTITY,edge(1),1,1,2,0,0).isEmpty());
        assertFalse(c.update(f,Pose.IDENTITY,edge(1),1,1,3_000_000_000L,0,0).isEmpty());
        Ref prior=f.refs.get(0);prior.valid=false;c.update(f,Pose.IDENTITY,edge(1),1,1,4_000_000_000L,0,0);assertTrue(prior.detached);
    }
}
