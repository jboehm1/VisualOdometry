package dev.jboehm.vectorvio;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import dev.jboehm.vectorvio.gps.*;
import java.util.*;
/** Offline route diagram. No tiles or implicit road network. */
public final class GpsRouteView extends View {
    interface Selection {void select(List<Double> candidates);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
    private GpsRoute route;private GpsNavigationService.State state;private Selection selection;
    private double[] x,y;private double lat0,lon0,cos,midX,midY,span=200;
    private float zoom=1,panX,panY,downX,downY;private final ScaleGestureDetector scale;private final GestureDetector gestures;
    public GpsRouteView(Context c){super(c);setBackgroundColor(0xff0b1722);
        scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.25f,Math.min(100,zoom*d.getScaleFactor()));invalidate();return true;}});
        gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(android.view.MotionEvent e){return true;}@Override public void onLongPress(android.view.MotionEvent e){pick(e.getX(),e.getY());}@Override public boolean onDoubleTap(android.view.MotionEvent e){fit();return true;}});
    }
    void setSelection(Selection s){selection=s;}
    void setRoute(GpsRoute r){if(route==r)return;route=r;fit();if(r==null){x=y=null;invalidate();return;}
        int n=r.points.size();x=new double[n];y=new double[n];lat0=r.points.get(0).lat;lon0=r.points.get(0).lon;cos=Math.max(.00001,Math.cos(Math.toRadians(lat0)));
        double unwrap=lon0,previous=lon0,lx=0,hx=0,ly=0,hy=0;
        for(int i=0;i<n;i++){GpsRoute.Point p=r.points.get(i);unwrap+=Math.IEEEremainder(p.lon-previous,360);previous=p.lon;x[i]=Geo.R*Math.toRadians(unwrap-lon0)*cos;y[i]=-Geo.R*Math.toRadians(p.lat-lat0);lx=Math.min(lx,x[i]);hx=Math.max(hx,x[i]);ly=Math.min(ly,y[i]);hy=Math.max(hy,y[i]);}
        midX=(lx+hx)/2;midY=(ly+hy)/2;span=Math.max(50,Math.max(hx-lx,hy-ly));invalidate();
    }
    void setState(GpsNavigationService.State s){state=s;invalidate();}
    void fit(){zoom=1;panX=panY=0;invalidate();}
    private float factor(){return (float)(Math.max(1,Math.min(getWidth()-60,getHeight()-80))/span*zoom);}
    private float sx(double a){return (float)(getWidth()/2.0+panX+(a-midX)*factor());}
    private float sy(double a){return (float)(getHeight()/2.0+panY+(a-midY)*factor());}
    private double gx(double lon){double a=Geo.R*Math.toRadians(Math.IEEEremainder(lon-lon0,360))*cos;double cycle=2*Math.PI*Geo.R*cos;return a+Math.rint((midX-a)/cycle)*cycle;}
    private double gy(double lat){return -Geo.R*Math.toRadians(lat-lat0);}
    private void dot(Canvas c,float px,float py,int color,float radius){paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawCircle(px,py,radius,paint);}
    private void text(Canvas c,String s,float px,float py,int color,float size){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size*getResources().getDisplayMetrics().scaledDensity);c.drawText(s,px,py,paint);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float density=getResources().getDisplayMetrics().density;
        text(c,"N ↑",16*density,24*density,0xffb6c8d4,12);
        if(route!=null&&x!=null){
            path.reset();int stride=Math.max(1,(x.length+5999)/6000);
            for(int i=0;i<x.length;i+=stride){if(i==0)path.moveTo(sx(x[i]),sy(y[i]));else path.lineTo(sx(x[i]),sy(y[i]));}path.lineTo(sx(x[x.length-1]),sy(y[y.length-1]));
            paint.setColor(0xff6ee6d6);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*density);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,paint);
            dot(c,sx(x[0]),sy(y[0]),0xffa8f0a4,6*density);dot(c,sx(x[x.length-1]),sy(y[y.length-1]),0xffffa8bb,6*density);
            double interval=route.length>100000?5000:1000;float lastX=-10000,lastY=-10000;
            for(int i=1;i<=80&&i*interval<route.length;i++){GpsRoute.Point p=route.at(i*interval);float px=sx(gx(p.lon)),py=sy(gy(p.lat));if(Math.hypot(px-lastX,py-lastY)<60*density)continue;if(px<10||px>getWidth()-60||py<35||py>getHeight()-30)continue;dot(c,px,py,0xffffd17b,3*density);text(c,String.format(Locale.FRANCE,"%.0f km",i*interval/1000),px+5*density,py-5*density,0xffffd17b,10);lastX=px;lastY=py;}
            if(state!=null&&state.route==route&&state.running&&state.status==GpsNavigator.Status.FOLLOWING){GpsRoute.Point p=route.at(state.progress);dot(c,sx(gx(p.lon)),sy(gy(p.lat)),0xffffd17b,5*density);}
        }
        if(state!=null&&state.fix!=null){GpsNavigator.Fix f=state.fix;boolean fresh=state.running&&android.os.SystemClock.elapsedRealtimeNanos()-f.ns<=5_000_000_000L;
            if(route==null){lat0=f.lat;lon0=f.lon;cos=Math.max(.00001,Math.cos(Math.toRadians(lat0)));midX=midY=0;span=200;}
            float px=sx(gx(f.lon)),py=sy(gy(f.lat));if(Double.isFinite(f.accuracy)&&f.accuracy>0){paint.setStyle(Paint.Style.FILL);paint.setColor(fresh?0x244d9fff:0x24888888);c.drawCircle(px,py,(float)Math.min(100000,f.accuracy*factor()),paint);}
            dot(c,px,py,fresh?0xff61aaff:0xff929da7,6*density);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Color.WHITE);c.drawCircle(px,py,7*density,paint);
        }
        text(c,"Trace hors ligne · sans fond cartographique",12*density,getHeight()-12*density,0xff9db0bf,10);
    }
    private void pick(float px,float py){if(route==null||selection==null)return;
        ArrayList<double[]> choices=new ArrayList<>();
        for(int i=1;i<x.length;i++){double ax=sx(x[i-1]),ay=sy(y[i-1]),dx=sx(x[i])-ax,dy=sy(y[i])-ay,len=dx*dx+dy*dy;
            double t=len>0?Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/len)):0,d=Math.hypot(px-ax-t*dx,py-ay-t*dy);
            if(d<40*getResources().getDisplayMetrics().density)choices.add(new double[]{route.progressAt(i-1)+t*(route.progressAt(i)-route.progressAt(i-1)),d});
        }
        choices.sort(Comparator.comparingDouble(a->a[1]));ArrayList<Double> result=new ArrayList<>();for(double[] a:choices){boolean duplicate=false;for(double s:result)if(Math.abs(s-a[0])<50)duplicate=true;if(!duplicate)result.add(a[0]);if(result.size()==8)break;}if(!result.isEmpty())selection.select(result);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){scale.onTouchEvent(e);gestures.onTouchEvent(e);if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();}else if(e.getActionMasked()==android.view.MotionEvent.ACTION_MOVE&&!scale.isInProgress()&&e.getPointerCount()==1){panX+=e.getX()-downX;panY+=e.getY()-downY;downX=e.getX();downY=e.getY();invalidate();}return true;}
}
