package dev.jboehm.vectorvio.gps;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** App-private canonical GPX copies; imports never depend on expiring document URIs. */
public final class RouteRepository {
    private final File root;
    public RouteRepository(Context c){root=new File(c.getFilesDir(),"gps_routes");}
    public File file(String id)throws IOException{if(id==null||!id.matches("route_[a-f0-9-]+\\.gpx"))throw new IOException("Invalid route identifier");return new File(root,id);}
    public List<GpsRoute> read(String id)throws Exception{try(InputStream in=new AtomicFile(file(id)).openRead()){return GpxIO.read(in);}}
    public synchronized String save(List<GpsRoute> routes)throws Exception{
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create route folder");
        File[] all=root.listFiles(f->f.getName().endsWith(".gpx"));long size=0;if(all!=null&&all.length>=200)throw new IOException("200 stored routes: remove unused routes first");File[] assets=root.listFiles(File::isFile);if(assets!=null)for(File f:assets)size+=f.length();
        if(size>64L*1024*1024||root.getUsableSpace()<32L*1024*1024)throw new IOException("Insufficient route storage");
        String id="route_"+UUID.randomUUID()+".gpx";AtomicFile atomic=new AtomicFile(file(id));FileOutputStream out=null;
        try{out=atomic.startWrite();GpxIO.write(routes,out);atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
        try(FileOutputStream label=new FileOutputStream(new File(root,id+".name"))){label.write(GpsRoute.cleanName(routes.get(0).name).getBytes(StandardCharsets.UTF_8));}return id;
    }
    public File journal(String id)throws IOException{return new File(root,file(id).getName()+".journal");}
    public void archiveJournal(String id,File source)throws IOException{if(!source.renameTo(journal(id)))throw new IOException("GPX saved; original journal retained for recovery");}
    public String label(String id){try{byte[] b=new byte[512];try(InputStream in=new FileInputStream(new File(root,id+".name"))){int n=in.read(b);return n>0?new String(b,0,n,StandardCharsets.UTF_8):id;}}catch(Exception e){return id;}}
    public List<String> list(){File[] files=root.listFiles(f->f.isFile()&&f.getName().matches("route_[a-f0-9-]+\\.gpx"));if(files==null)return Collections.emptyList();Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));ArrayList<String> ids=new ArrayList<>();for(File f:files)ids.add(f.getName());return ids;}
    public void delete(String id)throws IOException{File f=file(id),j=journal(id);if(j.exists()&&!j.delete())throw new IOException("Cannot delete GPS journal");if(f.exists()&&!f.delete())throw new IOException("Cannot delete route");new File(root,id+".name").delete();}
}
