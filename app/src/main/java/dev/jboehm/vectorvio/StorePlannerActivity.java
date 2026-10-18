package dev.jboehm.vectorvio;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import android.content.SharedPreferences;
import dev.jboehm.vectorvio.store.StoreRoutePlanner;
import java.util.*;

/** Local, manual offline grocery floorplan MVP; no camera or background location. */
public final class StorePlannerActivity extends Activity {
    private EditText nodeInput,edgeInput,productInput,listInput,startInput,endInput;
    private TextView result;
    private StoreMapView diagram;
    private static final String PREF="store_manual_v1";
    private static final String[] KEYS={"nodes","edges","products","shopping","start","checkout"};
    private static final String[] EXAMPLE={
        "ENTREE;0;0\nFRUITS;3;0\nPAIN;7;0\nLAIT;7;5\nCAISSE;12;5",
        "ENTREE;FRUITS;3\nFRUITS;PAIN;4\nPAIN;LAIT;5\nLAIT;CAISSE;5",
        "pommes;FRUITS\npain;PAIN\nlait;LAIT", "lait\npommes\npain", "ENTREE", "CAISSE"};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        SharedPreferences p=getSharedPreferences(PREF,MODE_PRIVATE);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xff0b1722);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(16),dp(14),dp(24));sc.addView(root);
        label(root,"COURSES · PLAN LOCAL HORS LIGNE",20,0xff72f5d8);
        label(root,"MVP manuel : renseigner les passages réellement praticables, associer les articles aux rayons, puis calculer l'ordre. Aucun rayon, chemin, prix ou localisation n'est détecté automatiquement. Exemple fictif à remplacer.",13,0xffcadce3);
        diagram=new StoreMapView();root.addView(diagram,new LinearLayout.LayoutParams(-1,dp(260)));
        nodeInput=field(root,"Noeuds · nom ; X ; Y (plan schématique)",p.getString(KEYS[0],EXAMPLE[0]),4);
        edgeInput=field(root,"Passages confirmés · noeud1 ; noeud2 ; mètres",p.getString(KEYS[1],EXAMPLE[1]),4);
        productInput=field(root,"Articles · nom produit ; rayon",p.getString(KEYS[2],EXAMPLE[2]),3);
        listInput=field(root,"Liste d'achats · un article par ligne",p.getString(KEYS[3],EXAMPLE[3]),3);
        startInput=field(root,"Entrée · nom du noeud",p.getString(KEYS[4],EXAMPLE[4]),1);
        endInput=field(root,"Caisse / sortie · nom du noeud",p.getString(KEYS[5],EXAMPLE[5]),1);
        Button compute=new Button(this);compute.setText("CALCULER PARCOURS");compute.setTextColor(0xff001b20);compute.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff72f5d8));
        root.addView(compute,new LinearLayout.LayoutParams(-1,dp(54)));
        result=label(root,"Les connexions sont manuelles; les longueurs doivent être réalistes. Le trajet n'est pas une consigne de sécurité.",14,Color.WHITE);
        compute.setOnClickListener(v->calculate());setContentView(sc);calculate();
    }
    private TextView label(LinearLayout root,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(8));root.addView(t);return t;}
    private EditText field(LinearLayout root,String title,String content,int lines){
        label(root,title,12,0xffa7c4d0);EditText f=new EditText(this);f.setText(content);f.setTextSize(13);
        f.setTextColor(Color.WHITE);f.setHintTextColor(0xffb0bdc5);f.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff427380));
        f.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        f.setMinLines(lines);f.setMaxLines(Math.max(lines,6));root.addView(f,new LinearLayout.LayoutParams(-1,-2));return f;
    }
    private static String[] parts(String row,int count){String[] p=row.split(";",-1);if(p.length!=count)throw new IllegalArgumentException("Colonnes attendues "+count+" : "+row);for(int i=0;i<p.length;i++)p[i]=p[i].trim();return p;}
    private static List<String> lines(String text){ArrayList<String> a=new ArrayList<>();for(String row:text.split("\\r?\\n"))if(!row.trim().isEmpty())a.add(row.trim());return a;}
    private void calculate(){
        String[] values={nodeInput.getText().toString(),edgeInput.getText().toString(),productInput.getText().toString(),listInput.getText().toString(),startInput.getText().toString(),endInput.getText().toString()};
        try{
            StoreRoutePlanner g=new StoreRoutePlanner();
            for(String row:lines(values[0])){String[] p=parts(row,3);g.addNode(p[0],Double.parseDouble(p[1]),Double.parseDouble(p[2]));}
            for(String row:lines(values[1])){String[] p=parts(row,3);g.connect(p[0],p[1],Double.parseDouble(p[2]));}
            for(String row:lines(values[2])){String[] p=parts(row,2);g.assignProduct(p[0],p[1]);}
            StoreRoutePlanner.Plan plan=g.plan(lines(values[3]),values[4].trim(),values[5].trim());
            StringBuilder out=new StringBuilder();out.append(plan.success?"TRACÉ ESTIMÉ (PLAN MANUEL)":"PLAN INCOMPLET — corriger avant utilisation");
            out.append(String.format(Locale.FRANCE,"\nDistance sur liaisons déclarées : %.1f m",plan.distanceMeters));
            out.append("\nOrdre d'achat : ").append(String.join(" → ",plan.productsInVisitOrder));
            out.append("\nPassages : ").append(String.join(" → ",plan.walkNodes));
            if(!plan.unassigned.isEmpty())out.append("\nArticles sans rayon : ").append(plan.unassigned);
            if(!plan.unreachable.isEmpty())out.append("\nRayons inaccessibles : ").append(plan.unreachable);
            out.append("\nAucune géolocalisation automatique ni reconnaissance des allées.");
            result.setText(out.toString());diagram.setGraph(g,plan);
            SharedPreferences.Editor editor=getSharedPreferences(PREF,MODE_PRIVATE).edit();for(int i=0;i<KEYS.length;i++)editor.putString(KEYS[i],values[i]);editor.apply();
        }catch(IllegalArgumentException e){result.setText("ERREUR DANS LE PLAN : "+e.getMessage());diagram.setGraph(null,null);}
    }
    private final class StoreMapView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private StoreRoutePlanner graph;private StoreRoutePlanner.Plan plan;
        StoreMapView(){super(StorePlannerActivity.this);setBackgroundColor(0xff142634);}
        void setGraph(StoreRoutePlanner graph,StoreRoutePlanner.Plan plan){this.graph=graph;this.plan=plan;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(graph==null)return;
            List<StoreRoutePlanner.Node> nodes=graph.nodes();if(nodes.isEmpty())return;
            HashMap<String,StoreRoutePlanner.Node> byId=new HashMap<>();double minX=Double.POSITIVE_INFINITY,maxX=-minX,minY=minX,maxY=-minX;
            for(StoreRoutePlanner.Node n:nodes){byId.put(n.id,n);minX=Math.min(minX,n.x);maxX=Math.max(maxX,n.x);minY=Math.min(minY,n.y);maxY=Math.max(maxY,n.y);}
            float margin=dp(36), sx=(getWidth()-2*margin)/(float)Math.max(1,maxX-minX),sy=(getHeight()-2*margin)/(float)Math.max(1,maxY-minY);
            float scale=Math.max(.001f,Math.min(sx,sy));final double ox=minX,oy=minY;
            java.util.function.Function<StoreRoutePlanner.Node,float[]> pos=n->new float[]{margin+(float)(n.x-ox)*scale,margin+(float)(n.y-oy)*scale};
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(0xff4f7380);
            for(StoreRoutePlanner.Edge e:graph.edges()){float[] a=pos.apply(byId.get(e.a)),b=pos.apply(byId.get(e.b));c.drawLine(a[0],a[1],b[0],b[1],paint);}
            if(plan!=null){paint.setColor(0xff6df0d0);paint.setStrokeWidth(dp(5));for(int i=1;i<plan.walkNodes.size();i++){
                StoreRoutePlanner.Node a=byId.get(plan.walkNodes.get(i-1)),b=byId.get(plan.walkNodes.get(i));if(a!=null&&b!=null){float[] p=pos.apply(a),q=pos.apply(b);c.drawLine(p[0],p[1],q[0],q[1],paint);}
            }}
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(10));
            for(StoreRoutePlanner.Node n:nodes){float[] p=pos.apply(n);paint.setColor(0xffe3f2f9);c.drawCircle(p[0],p[1],dp(4),paint);
                paint.setColor(0xffcadfe8);c.drawText(n.id,p[0]+dp(6),p[1]-dp(5),paint);}
        }
    }
}
