package dev.jboehm.vectorvio.gpxar;

import android.util.AtomicFile;
import dev.jboehm.vectorvio.gps.*;
import java.io.*;
import java.util.Collections;

/** One explicit selected continuous segment, app-private and atomically replaced. */
public final class GpxArRouteStore {
    private final AtomicFile file;
    public GpxArRouteStore(File directory){file=new AtomicFile(new File(directory,"gpx_ar_selected.gpx"));}
    public synchronized GpsRoute load()throws Exception{
        try(InputStream in=file.openRead()){return GpxIO.read(in).get(0);}
        catch(FileNotFoundException missing){return null;}
    }
    public synchronized void save(GpsRoute route)throws Exception{
        // Validate support before replacing the previous good route.
        new RouteIndex(route);
        FileOutputStream out=file.startWrite();
        try{GpxIO.write(Collections.singletonList(route),out);file.finishWrite(out);}
        catch(Exception e){file.failWrite(out);throw e;}
    }
    public synchronized void clear(){file.delete();}
}
