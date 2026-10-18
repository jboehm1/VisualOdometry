package dev.jboehm.vectorvio;

import android.os.Handler;
import android.os.SystemClock;
/** Independent tracking requests and presentation deadlines; no catch-up bursts. */
final class LiveFrameScheduler {
    static final int TRACKING_FPS=30;
    enum Mode {
        FULL("full",30), BALANCED("balanced",15), LOW("low",5), HOLD("hold",5);
        final String id;final int fps;
        Mode(String id,int fps){this.id=id;this.fps=fps;}
        static Mode from(String id){for(Mode m:values())if(m.id.equals(id))return m;return FULL;}
    }
    private final Handler handler;private final Runnable tracking,display;
    private boolean running,pressed;private volatile Mode mode=Mode.FULL;
    private volatile int targetFps=30;private long nextDisplayUs;
    private final Runnable tick=new Runnable(){public void run(){synchronized(LiveFrameScheduler.this){
        if(!running)return;
        tracking.run();
        long now=SystemClock.uptimeMillis()*1000;
        if(now>=nextDisplayUs){
            display.run();long period=1_000_000L/targetFps;
            nextDisplayUs+=((now-nextDisplayUs)/period+1)*period;
        }
        handler.postDelayed(this,33);
    }}};
    LiveFrameScheduler(Handler handler,Runnable tracking,Runnable display){this.handler=handler;this.tracking=tracking;this.display=display;}
    Mode mode(){return mode;}
    int fps(){return targetFps;}
    synchronized boolean pressed(){return pressed;}
    synchronized void setMode(Mode value){mode=value==null?Mode.FULL:value;pressed=false;changed();}
    synchronized void setPressed(boolean value){boolean next=value&&mode==Mode.HOLD;if(next==pressed)return;pressed=next;changed();}
    private void changed(){targetFps=mode==Mode.HOLD&&pressed?30:mode.fps;nextDisplayUs=SystemClock.uptimeMillis()*1000;if(running){display.run();nextDisplayUs+=1_000_000L/targetFps;}}
    synchronized void start(){if(running)return;running=true;nextDisplayUs=SystemClock.uptimeMillis()*1000;handler.post(tick);}
    synchronized void stop(){running=false;pressed=false;targetFps=mode.fps;handler.removeCallbacks(tick);}
}
