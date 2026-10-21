package dev.jboehm.vectorvio.pdr.research;

import java.util.ArrayDeque;

/** Research arbitration using only past emitted peaks, never placement or labels. */
public final class CausalArbitration {
    private final ArrayDeque<Long> baseline=new ArrayDeque<>(), filtered=new ArrayDeque<>();
    private long last=-1, emitted=-1, evidenceSince=-1;
    private boolean useFiltered;
    private int switches;

    public void reset() {
        baseline.clear();filtered.clear();last=emitted=evidenceSince=-1;
        useFiltered=false;switches=0;
    }

    public int switches() { return switches; }

    public boolean accept(long t, boolean base, boolean candidate) {
        if(t<=0 || t<=last)return false;
        if(last>0 && t-last>1_500_000_000L)reset();
        last=t;
        if(base)baseline.add(t);
        if(candidate)filtered.add(t);
        while(!baseline.isEmpty() && baseline.peekFirst()<t-8_000_000_000L)baseline.remove();
        while(!filtered.isEmpty() && filtered.peekFirst()<t-8_000_000_000L)filtered.remove();
        boolean supported=baseline.size()>=8 && filtered.size()>=8;
        double ratio=filtered.isEmpty()?1.:(double)baseline.size()/filtered.size();
        boolean change=supported && (useFiltered ? ratio<=1.05 : ratio>=1.15);
        if(change) {
            if(evidenceSince<0)evidenceSince=t;
            long duration=useFiltered?2_000_000_000L:1_000_000_000L;
            if(t-evidenceSince>=duration) {
                useFiltered=!useFiltered;switches++;evidenceSince=-1;
            }
        } else evidenceSince=-1;
        boolean chosen=useFiltered?candidate:base;
        if(!chosen || (emitted>0 && t-emitted<270_000_000L))return false;
        emitted=t;return true;
    }
}
