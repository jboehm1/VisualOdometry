package dev.jboehm.vectorvio.gps;
import org.xmlpull.v1.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Bounded GPX 1.0/1.1 reader. Never resolves DTD or external entities. */
public final class GpxIO {
    public static final int MAX_BYTES=12*1024*1024,MAX_SEGMENTS=256;
    private static boolean ns(String s){return s==null||s.isEmpty()||s.equals("http://www.topografix.com/GPX/1/1")||s.equals("http://www.topografix.com/GPX/1/0");}
    public static List<GpsRoute> read(InputStream source)throws Exception{
        InputStream input=new FilterInputStream(source){int count;public int read()throws IOException{int n=super.read();if(n>=0&&++count>MAX_BYTES)throw new IOException("GPX exceeds 12 MiB");return n;}public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0&&(count+=n)>MAX_BYTES)throw new IOException("GPX exceeds 12 MiB");return n;}};
        XmlPullParserFactory factory=XmlPullParserFactory.newInstance();factory.setNamespaceAware(true);XmlPullParser p=factory.newPullParser();if(p.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)){p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);if(p.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))throw new IOException("Unsafe XML parser");}p.setInput(input,null);
        ArrayList<GpsRoute> routes=new ArrayList<>();ArrayList<GpsRoute.Point> points=null;
        String name="Parcours",rootNs=null;int root=0,container=0,segment=0,pointDepth=0,count=0;double lat=0,lon=0,ele=Double.NaN;
        String capture=null;StringBuilder text=new StringBuilder();int textDepth=0;
        for(int event=p.nextToken();event!=XmlPullParser.END_DOCUMENT;event=p.nextToken()){
            if(event==XmlPullParser.DOCDECL)throw new IOException("DTD forbidden in GPX");
            if(event==XmlPullParser.ENTITY_REF){String entity=p.getName();if(!Arrays.asList("amp","lt","gt","apos","quot").contains(entity)&&!(entity!=null&&entity.startsWith("#")))throw new IOException("Entity forbidden");}
            if(p.getDepth()>32)throw new IOException("GPX nesting too deep");
            if(event==XmlPullParser.START_TAG){
                String tag=p.getName();int d=p.getDepth();
                if(d==1){if(!tag.equals("gpx")||!ns(p.getNamespace()))throw new IOException("Expected GPX 1.0/1.1");root=1;rootNs=p.getNamespace();}
                if(!Objects.equals(rootNs,p.getNamespace()))continue;
                if(d==2&&(tag.equals("trk")||tag.equals("rte"))){container=d;name="Parcours";if(tag.equals("rte")){points=new ArrayList<>();segment=d;}}
                else if(container==2&&d==3&&tag.equals("trkseg")){if(points!=null)throw new IOException("Nested segment");points=new ArrayList<>();segment=d;}
                else if(container==2&&d==3&&tag.equals("name")){capture="name";text.setLength(0);textDepth=d;}
                else if(points!=null&&d==segment+1&&((segment==3&&tag.equals("trkpt"))||(segment==2&&tag.equals("rtept")))){
                    lat=number(p.getAttributeValue(null,"lat"));lon=number(p.getAttributeValue(null,"lon"));ele=Double.NaN;pointDepth=d;
                    if(!Geo.valid(lat,lon))throw new IOException("Invalid GPX coordinate");if(++count>GpsRoute.MAX_POINTS)throw new IOException("GPX exceeds 50000 points");
                }else if(pointDepth>0&&d==pointDepth+1&&tag.equals("ele")){capture="ele";text.setLength(0);textDepth=d;}
            }else if((event==XmlPullParser.TEXT||event==XmlPullParser.CDSECT||event==XmlPullParser.ENTITY_REF)&&capture!=null){if(p.getText()!=null)text.append(p.getText());if(text.length()>4096)throw new IOException("GPX text too long");}
            else if(event==XmlPullParser.END_TAG){String tag=p.getName();int d=p.getDepth();if(!Objects.equals(rootNs,p.getNamespace()))continue;
                if(capture!=null&&d==textDepth){if(capture.equals("name"))name=GpsRoute.cleanName(text.toString());else ele=number(text.toString());capture=null;}
                if(pointDepth==d&&(tag.equals("trkpt")||tag.equals("rtept"))){points.add(new GpsRoute.Point(lat,lon,ele));pointDepth=0;}
                if(points!=null&&d==segment&&(tag.equals("trkseg")||tag.equals("rte"))){
                    if(points.size()>=2){routes.add(new GpsRoute(name+(routes.isEmpty()?"":" · segment "+(routes.size()+1)),points));if(routes.size()>MAX_SEGMENTS)throw new IOException("Too many GPX segments");}
                    points=null;segment=0;
                }
                if(d==container&&(tag.equals("trk")||tag.equals("rte")))container=0;
            }
        }
        if(root==0||routes.isEmpty())throw new IOException("No continuous track/route with at least two points");return Collections.unmodifiableList(routes);
    }
    private static double number(String s)throws IOException{try{double d=Double.parseDouble(s);if(!Double.isFinite(d))throw new NumberFormatException();return d;}catch(Exception e){throw new IOException("Invalid GPX number");}}
    public static void write(List<GpsRoute> routes,OutputStream output)throws IOException{
        Writer w=new OutputStreamWriter(output,StandardCharsets.UTF_8);w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"VECTOR\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        for(GpsRoute r:routes){w.write("<trk><name>"+escape(r.name)+"</name><trkseg>\n");for(GpsRoute.Point p:r.points){w.write("<trkpt lat=\""+Double.toString(p.lat)+"\" lon=\""+Double.toString(p.lon)+"\">");if(Double.isFinite(p.ele))w.write("<ele>"+Double.toString(p.ele)+"</ele>");w.write("</trkpt>\n");}w.write("</trkseg></trk>\n");}w.write("</gpx>\n");w.flush();
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
}
