package dev.jboehm.vectorvio;

import android.os.Handler;
/** UI-thread demand-driven frame requests. ARCore's internal camera/tracking remain active. */
final class LiveFrameScheduler {
    enum Mode {
        FULL("full",30), BALANCED("balanced",15), LOW("low",5), HOLD("hold",5);
        final String id;final int fps;
        Mode(String id,int fps){this.id=id;this.fps=fps;}
        static Mode from(String id){for(Mode m:values())if(m.id.equals(id))return m;return FULL;}
    }
    private final Handler handler;private final Runnable request;
    private boolean running,pressed;private Mode mode=Mode.FULL;
    private volatile int targetFps=30;
    private final Runnable tick=new Runnable(){public void run(){
        if(!running)return;
        request.run();
        // No catch-up bursts: a slow device skips scheduling opportunities.
        handler.postDelayed(this,Math.max(1,Math.round(1000f/targetFps)));
    }};
    LiveFrameScheduler(Handler handler,Runnable request){this.handler=handler;this.request=request;}
    Mode mode(){return mode;}
    int fps(){return targetFps;}
    boolean pressed(){return pressed;}
    void setMode(Mode value){mode=value==null?Mode.FULL:value;pressed=false;changed();}
    void setPressed(boolean value){boolean next=value&&mode==Mode.HOLD;if(next==pressed)return;pressed=next;changed();}
    private void changed(){targetFps=mode==Mode.HOLD&&pressed?30:mode.fps;if(running){handler.removeCallbacks(tick);handler.post(tick);}}
    void start(){if(running)return;running=true;handler.post(tick);}
    void stop(){running=false;pressed=false;targetFps=mode.fps;handler.removeCallbacks(tick);}
}
