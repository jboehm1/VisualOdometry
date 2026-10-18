package dev.jboehm.vectorvio.location;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * Owns finalized private A/B sessions. Directory-only allowlist prevents deleting
 * unrelated app files. No cloud/network data access.
 */
public final class LocationComparisonRepository {
    private final File root;
    private static final String NAME_RE="location_ab_[0-9]{8}_[0-9]{6}_[a-f0-9]{8}";
    private static final int MAX_ENTRIES=200;

    public LocationComparisonRepository(File root){this.root=root;}
    public List<File> listFinalized(){
        File[] candidates=root.listFiles();
        if(candidates==null)return Collections.emptyList();
        ArrayList<File> result=new ArrayList<>();
        for(File dir:candidates){
            if(result.size()>=MAX_ENTRIES)break;
            if(authorized(dir)&&finalized(dir))result.add(dir);
        }
        result.sort((a,b)->b.getName().compareTo(a.getName()));
        return result;
    }
    private boolean authorized(File dir){
        if(dir==null||!dir.getName().matches(NAME_RE)||!dir.isDirectory())return false;
        try{
            return !Files.isSymbolicLink(dir.toPath())&&
                dir.getCanonicalFile().getParentFile().equals(root.getCanonicalFile());
        }catch(IOException e){return false;}
    }
    private static boolean finalized(File dir){
        return new File(dir,LocationComparisonRecorder.META_JSON).isFile()&&
            new File(dir,LocationComparisonRecorder.RAW_CSV).isFile()&&
            new File(dir,LocationComparisonRecorder.PAIRS_CSV).isFile();
    }
    public File latest(){
        List<File> all=listFinalized();
        return all.isEmpty()?null:all.get(0);
    }
    public void deleteFinalized(File dir)throws IOException{
        if(!authorized(dir)||!finalized(dir))throw new IOException("Not an authorized finalized location session");
        File[] members=dir.listFiles();
        if(members==null||members.length!=3)throw new IOException("Unexpected files; deletion refused");
        Set<String> allowed=new HashSet<>(Arrays.asList(LocationComparisonRecorder.META_JSON,
            LocationComparisonRecorder.RAW_CSV,LocationComparisonRecorder.PAIRS_CSV));
        for(File f:members)if(!allowed.contains(f.getName())||!f.isFile()||
                Files.isSymbolicLink(f.toPath())||
                !f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))
            throw new IOException("Unexpected session content; deletion refused");
        for(File f:members)if(!f.delete())throw new IOException("Cannot delete "+f.getName());
        if(!dir.delete())throw new IOException("Could not remove empty session directory");
    }
}
