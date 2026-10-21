package dev.jboehm.vectorvio;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
/** Capture-only storage: lossless compressed sensor logs; no video re-encoding on export. */
final class CollectionFiles {
    static BufferedWriter log(File dir,String name,String header)throws IOException{
        GZIPOutputStream gzip=new GZIPOutputStream(new FileOutputStream(new File(dir,name)),65536,true);
        BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(gzip,StandardCharsets.UTF_8),65536);
        writer.write(header);writer.newLine();return writer;
    }
    static void zip(File directory,OutputStream output)throws IOException{
        File[] files=directory.listFiles(File::isFile);if(files==null)throw new IOException("Capture not found");
        Arrays.sort(files,Comparator.comparing(File::getName));
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            zip.setLevel(Deflater.NO_COMPRESSION);byte[] buffer=new byte[65536];
            for(File file:files){zip.putNextEntry(new ZipEntry(file.getName()));try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();}
        }
    }
    static boolean isSession(String name){return name.matches("capture_[0-9]{8}_[0-9]{6}_[0-9]{3}");}
}
