package dev.jboehm.vectorvio.store;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local named, versioned, atomic shopping maps. No camera/location/network access. */
public final class StorePlanRepository {
    private static final int VERSION=1, FIELDS=6, MAX_BYTES=128*1024, MAX_STORES=100;
    private static final String ID_PATTERN="store_[a-f0-9-]{36}\\.json";
    private final File directory;
    public StorePlanRepository(Context context){directory=new File(context.getFilesDir(),"store_plans");}
    public static final class Stored {
        public final String id,name;
        public final String[] fields;
        public Stored(String id,String name,String[] fields){this.id=id;this.name=name;this.fields=fields.clone();}
    }
    private static void check(String name,String[] fields){
        if(name==null||name.trim().isEmpty()||name.length()>100||fields==null||fields.length!=FIELDS)
            throw new IllegalArgumentException("Nom ou champs de magasin invalides");
        for(String value:fields)if(value==null||value.length()>40000)
            throw new IllegalArgumentException("Champ de magasin trop grand");
    }
    public static String encode(String name,String[] fields)throws Exception{
        check(name,fields);
        JSONObject json=new JSONObject();
        json.put("format","VECTOR_STORE_PLAN");json.put("version",VERSION);json.put("name",name.trim());
        JSONArray values=new JSONArray();for(String item:fields)values.put(item);
        json.put("fields",values);
        String serialized=json.toString(2);
        if(serialized.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IOException("Magasin trop volumineux");
        return serialized;
    }
    public static Stored decode(String id,String data)throws Exception{
        if(data.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IOException("Magasin trop volumineux");
        JSONObject json=new JSONObject(data);
        if(!"VECTOR_STORE_PLAN".equals(json.optString("format"))||json.getInt("version")!=VERSION)
            throw new IOException("Format ou version de plan inconnu");
        JSONArray a=json.getJSONArray("fields");
        if(a.length()!=FIELDS)throw new IOException("Champs du plan incomplets");
        String[] values=new String[FIELDS];for(int i=0;i<FIELDS;i++)values[i]=a.getString(i);
        String name=json.getString("name");check(name,values);
        return new Stored(id,name,values);
    }
    private File target(String id)throws IOException{
        if(id==null||!id.matches(ID_PATTERN))throw new IOException("Identifiant de plan invalide");
        return new File(directory,id);
    }
    public synchronized Stored save(String name,String[] fields)throws Exception{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Stockage inaccessible");
        File[] current=directory.listFiles(f->f.getName().matches(ID_PATTERN));
        if(current!=null&&current.length>=MAX_STORES)throw new IOException("100 magasins enregistrés : supprimer un ancien plan");
        String content=encode(name,fields);
        String id="store_"+UUID.randomUUID()+".json";
        AtomicFile atomic=new AtomicFile(target(id));FileOutputStream out=null;
        try{
            out=atomic.startWrite();out.write(content.getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(out);
        }catch(Exception ex){if(out!=null)atomic.failWrite(out);throw ex;}
        return new Stored(id,name.trim(),fields);
    }
    public Stored read(String id)throws Exception{
        AtomicFile atomic=new AtomicFile(target(id));
        try(InputStream input=atomic.openRead()){
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] tmp=new byte[8192];int n;
            while((n=input.read(tmp))!=-1){
                if(buffer.size()+n>MAX_BYTES)throw new IOException("Plan trop volumineux");
                buffer.write(tmp,0,n);
            }
            return decode(id,new String(buffer.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    public List<Stored> list(){
        File[] files=directory.listFiles(f->f.isFile()&&f.getName().matches(ID_PATTERN));
        if(files==null)return List.of();
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        ArrayList<Stored> entries=new ArrayList<>();
        for(File file:files){
            try{entries.add(read(file.getName()));}
            catch(Exception ignored){/* A corrupt plan must not hide the other plans. */}
        }
        return Collections.unmodifiableList(entries);
    }
    public void delete(String id)throws IOException{
        File target=target(id);
        if(target.exists()&&!target.delete())throw new IOException("Impossible de supprimer le plan");
        // AtomicFile backups may exist after interrupted saves; remove explicitly.
        File backup=new File(target.getPath()+".bak");
        if(backup.exists()&&!backup.delete())throw new IOException("Impossible de supprimer la sauvegarde du plan");
    }
}
