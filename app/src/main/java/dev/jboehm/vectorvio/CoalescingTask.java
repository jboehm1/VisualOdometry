package dev.jboehm.vectorvio;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
/** At most one queued/running task; a slow consumer never accumulates stale work. */
final class CoalescingTask {
    private final AtomicBoolean pending=new AtomicBoolean();
    private final Consumer<Runnable> dispatch;private final Runnable work;
    CoalescingTask(Consumer<Runnable> dispatch,Runnable work){this.dispatch=dispatch;this.work=work;}
    void offer(){
        if(!pending.compareAndSet(false,true))return;
        try{dispatch.accept(()->{try{work.run();}finally{pending.set(false);}});}
        catch(RuntimeException|Error e){pending.set(false);throw e;}
    }
}
