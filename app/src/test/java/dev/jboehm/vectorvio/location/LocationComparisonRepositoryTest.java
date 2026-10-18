package dev.jboehm.vectorvio.location;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Session history must not trap or inadvertently delete private location logs. */
public class LocationComparisonRepositoryTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();

    private File complete(File root,long start)throws Exception{
        File dir;
        try(LocationComparisonRecorder rec=new LocationComparisonRecorder(root,start,"S21")){
            dir=rec.directory();
            rec.finish("USER_STOP",new LocationPairer().snapshot(),start+1000);
        }
        return dir;
    }
    @Test public void completeSessionsSurviveActivityRecreationAndCanBeDeleted()throws Exception {
        File root=tmp.newFolder("sessions");
        File one=complete(root,1_700_000_000_000L);
        LocationComparisonRepository repo=new LocationComparisonRepository(root);
        assertEquals(1,repo.listFinalized().size());
        assertEquals(one.getCanonicalFile(),repo.latest().getCanonicalFile());
        LocationComparisonRepository fresh=new LocationComparisonRepository(root);
        assertEquals(1,fresh.listFinalized().size());
        fresh.deleteFinalized(one);
        assertNull(fresh.latest());assertFalse(one.exists());
    }
    @Test public void unrelatedPathsCannotBeDeleted()throws Exception {
        File root=tmp.newFolder("sessions");
        File anotherRoot=tmp.newFolder("elsewhere");
        File foreign=complete(anotherRoot,1_700_000_000_000L);
        LocationComparisonRepository repo=new LocationComparisonRepository(root);
        try{
            repo.deleteFinalized(foreign);fail("Must refuse foreign path");
        }catch(IOException expected){assertTrue(foreign.exists());}
    }
    @Test public void incompleteSessionsAreNotPresentedAsExportable()throws Exception {
        File root=tmp.newFolder("sessions");
        File incomplete=new File(root,"location_ab_20261018_123456_a1b2c3d4");
        assertTrue(incomplete.mkdir());
        LocationComparisonRepository repo=new LocationComparisonRepository(root);
        assertTrue(repo.listFinalized().isEmpty());
        try{
            repo.deleteFinalized(incomplete);fail("Must refuse incomplete session");
        }catch(IOException expected){assertTrue(incomplete.exists());}
    }
    @Test public void unexpectedMemberProtectsSessionFromDeletion()throws Exception {
        File root=tmp.newFolder("sessions");
        File dir=complete(root,1_700_000_000_000L);
        assertTrue(new File(dir,"not-ours.txt").createNewFile());
        try{
            new LocationComparisonRepository(root).deleteFinalized(dir);
            fail("Should not delete extra files");
        }catch(IOException expected){assertTrue(dir.exists());}
    }
}
