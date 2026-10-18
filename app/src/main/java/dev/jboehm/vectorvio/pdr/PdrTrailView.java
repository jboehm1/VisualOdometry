package dev.jboehm.vectorvio.pdr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.List;
import java.util.Locale;

/** Screen-only preview. Local navigation estimates MUST NOT look like surveyed routes. */
public final class PdrTrailView extends View {
    private final Paint p=new Paint(3);
    private List<PdrEngine.Point> path;
    public PdrTrailView(Context c){super(c);setBackgroundColor(0xFF0C2430);}
    public void setPath(List<PdrEngine.Point> data){path=data;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        p.setStrokeWidth(1);p.setColor(0x443C6675);
        for(int i=1;i<5;i++){
            c.drawLine(w*i/5,0,w*i/5,h,p);
            c.drawLine(0,h*i/5,w,h*i/5,p);
        }
        if(path==null || path.size()<2){
            p.setColor(0xFFB7CBD3);p.setTextSize(15*getResources().getDisplayMetrics().scaledDensity);
            c.drawText("No directional path yet",18,h/2,p);return;
        }
        double minE=0,maxE=0,minN=0,maxN=0;
        for(PdrEngine.Point a:path) {
            minE=Math.min(minE,a.eastM);maxE=Math.max(maxE,a.eastM);
            minN=Math.min(minN,a.northM);maxN=Math.max(maxN,a.northM);
        }
        double span=Math.max(5,Math.max(maxE-minE,maxN-minN));
        double scale=Math.min(w,h)*.78/span;
        double midE=(minE+maxE)/2,midN=(minN+maxN)/2;
        p.setColor(0xFF60F2C7);p.setStrokeWidth(3*getResources().getDisplayMetrics().density);
        for(int i=1;i<path.size();i++){
            PdrEngine.Point a=path.get(i-1),b=path.get(i);
            if(b.gapBefore)continue;
            c.drawLine((float)(w/2+(a.eastM-midE)*scale),(float)(h/2-(a.northM-midN)*scale),
                    (float)(w/2+(b.eastM-midE)*scale),(float)(h/2-(b.northM-midN)*scale),p);
        }
        PdrEngine.Point last=path.get(path.size()-1);
        p.setColor(Color.WHITE);
        c.drawCircle((float)(w/2+(last.eastM-midE)*scale),
                (float)(h/2-(last.northM-midN)*scale),5*getResources().getDisplayMetrics().density,p);
        p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        c.drawText(String.format(Locale.US,"LOCAL · %.0f m scale · N up",span),12,20,p);
    }
}
