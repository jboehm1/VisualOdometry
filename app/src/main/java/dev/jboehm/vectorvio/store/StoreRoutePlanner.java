package dev.jboehm.vectorvio.store;

import java.util.*;

/**
 * Offline, local, manually validated walkable-aisle graph and shopping-order planner.
 * Geometry is ONLY schematic: edges/lengths must be provided by a human.
 * Never invent traversable connections from proximity on a 2D drawing.
 */
public final class StoreRoutePlanner {
    private static final int MAX_NODES=400, MAX_EDGES=1200, MAX_PRODUCTS=600, MAX_STOPS=100;
    public static final class Node {
        public final String id;public final double x,y;
        public Node(String id,double x,double y){this.id=id;this.x=x;this.y=y;}
    }
    public static final class Edge {
        public final String a,b;public final double meters;
        Edge(String a,String b,double meters){this.a=a;this.b=b;this.meters=meters;}
    }
    public static final class Plan {
        public final boolean success;
        public final List<String> productsInVisitOrder,visitNodes,walkNodes,unassigned,unreachable;
        public final double distanceMeters;
        public final String message;
        Plan(boolean success,List<String> products,List<String> stops,List<String> path,List<String> unassigned,List<String> unreachable,double distance,String message){
            this.success=success;this.productsInVisitOrder=List.copyOf(products);this.visitNodes=List.copyOf(stops);
            this.walkNodes=List.copyOf(path);this.unassigned=List.copyOf(unassigned);this.unreachable=List.copyOf(unreachable);
            this.distanceMeters=distance;this.message=message;
        }
    }
    private static final class Link {
        final String to;final double meters;
        Link(String to,double meters){this.to=to;this.meters=meters;}
    }
    private static final class Path {
        final double length;final List<String> nodes;
        Path(double length,List<String> nodes){this.length=length;this.nodes=nodes;}
    }
    private static final class QueueEntry {
        final String node;final double dist;
        QueueEntry(String node,double dist){this.node=node;this.dist=dist;}
    }
    private final LinkedHashMap<String,Node> nodes=new LinkedHashMap<>();
    private final LinkedHashMap<String,List<Link>> links=new LinkedHashMap<>();
    private final ArrayList<Edge> edges=new ArrayList<>();
    private final HashMap<String,String> productLocation=new HashMap<>();
    private final Map<String,Path> cache=new HashMap<>();
    private static String id(String s){if(s==null||s.trim().isEmpty()||s.length()>100)throw new IllegalArgumentException("Identifiant vide/trop long");return s.trim();}
    private static String key(String s){return id(s).toLowerCase(Locale.ROOT);}
    public void addNode(String node,double x,double y){
        node=id(node);if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Coordonnees non finies");
        if(nodes.size()>=MAX_NODES)throw new IllegalArgumentException("Trop de noeuds");
        if(nodes.containsKey(node))throw new IllegalArgumentException("Noeud duplique: "+node);
        nodes.put(node,new Node(node,x,y));links.put(node,new ArrayList<>());cache.clear();
    }
    public void connect(String a,String b,double lengthMeters){
        a=id(a);b=id(b);if(!nodes.containsKey(a)||!nodes.containsKey(b)||a.equals(b)||!Double.isFinite(lengthMeters)||lengthMeters<=0||lengthMeters>10000)
            throw new IllegalArgumentException("Liaison impossible: "+a+" / "+b);
        if(edges.size()>=MAX_EDGES)throw new IllegalArgumentException("Trop de liaisons");
        for(Edge edge:edges)if((edge.a.equals(a)&&edge.b.equals(b))||(edge.a.equals(b)&&edge.b.equals(a)))
            throw new IllegalArgumentException("Liaison dupliquee");
        edges.add(new Edge(a,b,lengthMeters));links.get(a).add(new Link(b,lengthMeters));links.get(b).add(new Link(a,lengthMeters));cache.clear();
    }
    public void assignProduct(String product,String node){
        String k=key(product);node=id(node);
        if(!nodes.containsKey(node)||productLocation.size()>=MAX_PRODUCTS)throw new IllegalArgumentException("Rayon inconnu ou trop d'articles: "+node);
        productLocation.put(k,node);
    }
    public List<Node> nodes(){return List.copyOf(nodes.values());}
    public List<Edge> edges(){return List.copyOf(edges);}
    private Path shortest(String from,String to){
        String cacheKey=from+"\u0000"+to;
        Path saved=cache.get(cacheKey);if(saved!=null)return saved;
        HashMap<String,Double> dist=new HashMap<>();HashMap<String,String> prev=new HashMap<>();
        PriorityQueue<QueueEntry> pq=new PriorityQueue<>(Comparator.comparingDouble(q->q.dist));
        dist.put(from,0d);pq.add(new QueueEntry(from,0));
        while(!pq.isEmpty()){
            QueueEntry u=pq.poll();if(u.dist>dist.getOrDefault(u.node,Double.POSITIVE_INFINITY))continue;
            if(u.node.equals(to))break;
            for(Link link:links.get(u.node)){
                double alt=u.dist+link.meters;
                if(alt+1e-9<dist.getOrDefault(link.to,Double.POSITIVE_INFINITY)){
                    dist.put(link.to,alt);prev.put(link.to,u.node);pq.add(new QueueEntry(link.to,alt));
                }
            }
        }
        if(!dist.containsKey(to))return new Path(Double.POSITIVE_INFINITY,List.of());
        ArrayList<String> rev=new ArrayList<>();String node=to;rev.add(node);
        while(!node.equals(from)){node=prev.get(node);rev.add(node);}
        Collections.reverse(rev);Path path=new Path(dist.get(to),List.copyOf(rev));cache.put(cacheKey,path);
        return path;
    }
    /** Exact dynamic-programming optimum on the declared graph for small lists.
     * The shortest-path metric preserves optimal walk length even when legs pass
     * another stop earlier. For >11 stops the bounded heuristic is used.
     */
    private ArrayList<String> optimalOrder(List<String> available,String start,String finish){
        int n=available.size();
        if(n>11)return null;
        if(n==0)return new ArrayList<>();
        final int count=1<<n;
        double[][] best=new double[count][n];
        int[][] previous=new int[count][n];
        for(int mask=0;mask<count;mask++){
            Arrays.fill(best[mask],Double.POSITIVE_INFINITY);
            Arrays.fill(previous[mask],-1);
        }
        for(int j=0;j<n;j++)best[1<<j][j]=shortest(start,available.get(j)).length;
        for(int mask=1;mask<count;mask++)for(int end=0;end<n;end++){
            if((mask&(1<<end))==0 || !Double.isFinite(best[mask][end]))continue;
            for(int next=0;next<n;next++)if((mask&(1<<next))==0){
                int newMask=mask|(1<<next);
                double cost=best[mask][end]+shortest(available.get(end),available.get(next)).length;
                if(cost+1e-9<best[newMask][next]){
                    best[newMask][next]=cost;previous[newMask][next]=end;
                }
            }
        }
        int full=count-1,last=-1;double min=Double.POSITIVE_INFINITY;
        for(int i=0;i<n;i++){
            double value=best[full][i]+shortest(available.get(i),finish).length;
            if(value+1e-9<min){min=value;last=i;}
        }
        if(last<0 || !Double.isFinite(min))throw new IllegalStateException("Unreachable stops in exact planner");
        ArrayList<String> reversed=new ArrayList<>();int mask=full;
        while(last>=0){
            reversed.add(available.get(last));
            int prior=previous[mask][last];mask^=1<<last;last=prior;
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private double cost(List<String> order,String start,String finish){
        String curr=start;double total=0;
        for(String s:order){total+=shortest(curr,s).length;curr=s;}
        return total+shortest(curr,finish).length;
    }
    public Plan plan(List<String> shoppingList,String entrance,String checkout){
        entrance=id(entrance);checkout=id(checkout);
        if(!nodes.containsKey(entrance)||!nodes.containsKey(checkout))throw new IllegalArgumentException("Entree/caisse absentes du plan");
        if(shoppingList==null||shoppingList.size()>MAX_PRODUCTS)throw new IllegalArgumentException("Liste trop grande");
        LinkedHashMap<String,List<String>> byNode=new LinkedHashMap<>();ArrayList<String> unassigned=new ArrayList<>();
        for(String product:shoppingList){String p=id(product);String destination=productLocation.get(key(p));
            if(destination==null)unassigned.add(p);else byNode.computeIfAbsent(destination,k->new ArrayList<>()).add(p);
        }
        if(byNode.size()>MAX_STOPS)throw new IllegalArgumentException("Trop d'etapes distinctes");
        ArrayList<String> unreachable=new ArrayList<>();ArrayList<String> remaining=new ArrayList<>();
        if(!Double.isFinite(shortest(entrance,checkout).length))
            return new Plan(false,List.of(),List.of(),List.of(),unassigned,List.of(checkout),0,"Caisse non joignable depuis l'entree");
        for(String n:byNode.keySet()){
            if(!Double.isFinite(shortest(entrance,n).length)||!Double.isFinite(shortest(n,checkout).length))unreachable.add(n);
            else remaining.add(n);
        }
        ArrayList<String> ordered=optimalOrder(remaining,entrance,checkout);
        boolean heuristic=ordered==null;
        if(heuristic)ordered=new ArrayList<>();
        String cur=entrance;
        while(heuristic&&!remaining.isEmpty()){
            String best=null;double nearest=Double.POSITIVE_INFINITY;
            for(String candidate:remaining){double dist=shortest(cur,candidate).length;
                if(dist<nearest){nearest=dist;best=candidate;}}
            ordered.add(best);remaining.remove(best);cur=best;
        }
        // Bounded deterministic 2-opt only for larger lists.
        for(int pass=0;heuristic&&pass<3;pass++){
            boolean better=false;double old=cost(ordered,entrance,checkout);
            for(int i=0;i<ordered.size()&&!better;i++)for(int j=i+1;j<ordered.size();j++){
                Collections.reverse(ordered.subList(i,j+1));double candidate=cost(ordered,entrance,checkout);
                if(candidate+1e-8<old){better=true;break;}
                Collections.reverse(ordered.subList(i,j+1));
            }
            if(!better)break;
        }
        ArrayList<String> path=new ArrayList<>(),items=new ArrayList<>(),stops=new ArrayList<>();
        stops.add(entrance);stops.addAll(ordered);stops.add(checkout);
        double meters=0;
        for(int i=1;i<stops.size();i++){
            Path leg=shortest(stops.get(i-1),stops.get(i));
            if(!Double.isFinite(leg.length))throw new IllegalStateException("Incoherence de connectivite");
            for(int j=0;j<leg.nodes.size();j++)if(path.isEmpty()||j>0)path.add(leg.nodes.get(j));
            meters+=leg.length;
        }
        for(String n:ordered)items.addAll(byNode.get(n));
        boolean good=unassigned.isEmpty()&&unreachable.isEmpty();
        return new Plan(good,items,stops,path,unassigned,unreachable,meters,good?(heuristic?"Itineraire heuristique sur passages declares (non verifie sur place)":"Itineraire minimal sur passages declares (non verifie sur place)"):"Carte incomplete: articles ou rayons non accessibles");
    }
}
