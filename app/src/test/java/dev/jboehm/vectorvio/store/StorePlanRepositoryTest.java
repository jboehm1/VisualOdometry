package dev.jboehm.vectorvio.store;

import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class StorePlanRepositoryTest {
    @Test public void codecRoundTripMaintainsUnicodeAndNewlines()throws Exception{
        String[] items={"ENTREE;0;0\nFRUITS;2;1", "ENTREE;FRUITS;4", "œufs;FRUITS", "œufs", "ENTREE", "FRUITS"};
        String data=StorePlanRepository.encode("Marché à côté",items);
        StorePlanRepository.Stored copy=StorePlanRepository.decode("store_test"," "+data);
        assertEquals("Marché à côté",copy.name);
        assertArrayEquals(items,copy.fields);
    }
    @Test public void savesMultipleIndependentShopsAndDeletesSpecificCopy()throws Exception{
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        StorePlanRepository repo=new StorePlanRepository(activity);
        String[] a={"N1;0;0","", "", "lait","N1","N1"};
        String[] b={"N2;0;0","", "", "pain","N2","N2"};
        StorePlanRepository.Stored first=repo.save("Super A",a),second=repo.save("Super B",b);
        assertNotEquals(first.id,second.id);
        assertEquals(2,repo.list().size());
        assertArrayEquals(a,repo.read(first.id).fields);
        assertArrayEquals(b,repo.read(second.id).fields);
        repo.delete(first.id);
        assertEquals(1,repo.list().size());
        assertArrayEquals(b,repo.read(second.id).fields);
        activity.finish();
    }
    @Test public void refusesUnsafePathsAndUnknownVersion()throws Exception{
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        StorePlanRepository repo=new StorePlanRepository(activity);
        try{repo.read("../../etc/passwd");fail("Path traversal accepted");}
        catch(java.io.IOException expected){}
        String[] a={"n","e","p","l","s","e"};
        String encoded=StorePlanRepository.encode("Safe",a);
        try{StorePlanRepository.decode("fake",new org.json.JSONObject(encoded).put("version",99).toString());
            fail("Unknown version accepted");}catch(java.io.IOException expected){}
        activity.finish();
    }
}
