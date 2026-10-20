package dev.jboehm.vectorvio.vision;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class VisionPrototypeTest {
    @Test public void throttlesCameraWorkAndNeverOverridesNoCamera(){
        VisionTriggerPolicy p=new VisionTriggerPolicy();
        assertEquals(VisionTriggerPolicy.Decision.SKIP,p.request(1,true,VisionTriggerPolicy.Reason.NONE,false));
        assertEquals(VisionTriggerPolicy.Decision.CAMERA_UNAVAILABLE,p.request(1,false,VisionTriggerPolicy.Reason.GPX_AMBIGUOUS,false));
        assertEquals(VisionTriggerPolicy.Decision.RUN,p.request(1,true,VisionTriggerPolicy.Reason.GPX_AMBIGUOUS,false));
        assertEquals(VisionTriggerPolicy.Decision.COOL_DOWN,p.request(2,true,VisionTriggerPolicy.Reason.GPX_AMBIGUOUS,false));
        assertEquals(VisionTriggerPolicy.Decision.RUN,p.request(5_000_000_001L,true,VisionTriggerPolicy.Reason.UPCOMING_TURN,false));
    }
    @Test public void uniformFogOrSnowLikeFrameIsNotClaimedAsPath(){
        int[] pixels=new int[64*64];Arrays.fill(pixels,0xffeeeeee);
        assertEquals(TrailAppearanceBaseline.State.INSUFFICIENT_TEXTURE,
                TrailAppearanceBaseline.evaluate(pixels,64,64).state);
    }
    @Test public void producesOnlyUnvalidatedAppearanceProposal(){
        int[] rgb=new int[64*64];
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int noise=((x*71+y*37)%23)-11;
            int base=x>=24&&x<40?120:40;
            int c=Math.max(0,Math.min(255,base+noise));rgb[y*64+x]=(c<<16)|(c<<8)|c;
        }
        TrailAppearanceBaseline.Output r=TrailAppearanceBaseline.evaluate(rgb,64,64);
        assertEquals(TrailAppearanceBaseline.State.PROPOSAL,r.state);
        assertEquals(16*16,r.proposal.length);assertTrue(r.proposalRatio>0);assertTrue(r.proposalRatio<1);
    }
}
