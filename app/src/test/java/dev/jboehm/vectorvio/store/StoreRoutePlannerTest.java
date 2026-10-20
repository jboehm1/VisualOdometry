package dev.jboehm.vectorvio.store;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class StoreRoutePlannerTest {
    private StoreRoutePlanner example(){
        StoreRoutePlanner g=new StoreRoutePlanner();for(String n:new String[]{"ENTREE","A","B","C","CAISSE","ISOLE"})g.addNode(n,0,0);
        g.connect("ENTREE","A",2);g.connect("A","B",3);g.connect("B","C",2);g.connect("C","CAISSE",4);
        g.assignProduct("pommes","A");g.assignProduct("lait","C");g.assignProduct("pain","B");g.assignProduct("surprise","ISOLE");return g;
    }
    @Test public void picksPracticalAisleOrderAndRealEdges(){
        StoreRoutePlanner.Plan p=example().plan(List.of("lait","pommes","pain"),"ENTREE","CAISSE");
        assertTrue(p.message,p.success);assertEquals(11,p.distanceMeters,1e-8);
        assertEquals(List.of("pommes","pain","lait"),p.productsInVisitOrder);
        assertEquals(List.of("ENTREE","A","B","C","CAISSE"),p.walkNodes);
    }
    @Test public void warnsOnUnknownOrDisconnectedProducts(){
        StoreRoutePlanner.Plan p=example().plan(List.of("pommes","surprise","inconnu"),"ENTREE","CAISSE");
        assertFalse(p.success);assertEquals(List.of("ISOLE"),p.unreachable);assertEquals(List.of("inconnu"),p.unassigned);
        assertFalse(p.walkNodes.contains("ISOLE"));
    }
    @Test public void doesNotInventLineBetweenVisuallyClosePoints(){
        StoreRoutePlanner g=new StoreRoutePlanner();g.addNode("start",0,0);g.addNode("finish",0.001,0.001);
        g.assignProduct("item","finish");
        StoreRoutePlanner.Plan p=g.plan(List.of("item"),"start","start");
        assertFalse(p.success);assertEquals(List.of("finish"),p.unreachable);assertEquals(0,p.distanceMeters,0);
    }
    @Test public void exactOptimumFindsBetterOrderThanNearestNeighbour(){
        // A star-and-shortcut graph: greedy visits the nearby branch first;
        // exact DP compares all orders for global walk distance.
        StoreRoutePlanner g=new StoreRoutePlanner();
        for(String n:new String[]{"start","A","B","C","finish"})g.addNode(n,0,0);
        g.connect("start","A",1);g.connect("start","B",2);
        g.connect("A","C",20);g.connect("B","C",2);
        g.connect("C","finish",1);g.connect("A","finish",22);
        g.assignProduct("a","A");g.assignProduct("b","B");g.assignProduct("c","C");
        StoreRoutePlanner.Plan plan=g.plan(List.of("a","b","c"),"start","finish");
        assertTrue(plan.message,plan.success);
        assertTrue(plan.message,plan.message.contains("minimal"));
        // Brute force reference on the graph: include start->A->start->B->C->finish = 1+1+2+2+1=7.
        assertEquals(7,plan.distanceMeters,1e-8);
    }
    @Test public void canHandleZeroStopsWithoutInventedDistance(){
        StoreRoutePlanner g=new StoreRoutePlanner();g.addNode("start",0,0);g.addNode("end",0,0);g.connect("start","end",7);
        StoreRoutePlanner.Plan result=g.plan(List.of(),"start","end");
        assertTrue(result.success);assertEquals(7,result.distanceMeters,0);
        assertTrue(result.productsInVisitOrder.isEmpty());
    }
    @Test public void refusesBadInputs(){
        StoreRoutePlanner g=new StoreRoutePlanner();g.addNode("A",0,0);
        try{g.connect("A","A",1);fail("self-edge");}catch(IllegalArgumentException expected){}
        try{g.addNode("A",0,0);fail("duplicate");}catch(IllegalArgumentException expected){}
        try{g.assignProduct("x","not-there");fail("unknown");}catch(IllegalArgumentException expected){}
    }
}
