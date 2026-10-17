package dev.jboehm.vectorvio;

import android.graphics.*;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Actual Android Canvas rendering with synthetic fixtures. Not camera/device screenshots. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class VisualRenderTest {
    private void render(View view,String name,int width,int height) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xFF192E3D);view.draw(canvas);
        assertNotEquals(0,bitmap.getPixel(width/2,height/2));
        File directory=new File("build/reports/tests/visual-fixtures");assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    @Test public void rendersHudAndSensorModesAtPhoneSize()throws Exception {
        Odometer odometer=new Odometer();odometer.start(0);
        for(int i=0;i<100;i++)odometer.onPose(i*.1,0,Math.sin(i*.05),1_000_000_000L+i*100_000_000L);
        HudView hud=new HudView(RuntimeEnvironment.getApplication());
        hud.update(odometer.snapshot(12500),"TRACKING","SYNTHETIC UI FIXTURE · not a field measurement",2);
        List<SceneMapper.Shape> shapes=new ArrayList<>();
        shapes.add(new SceneMapper.Shape(new float[]{.05f,.47f,.7f,.38f,.95f,.58f,.3f,.67f},0,"HORIZONTAL  3.2 × 2.5 m"));
        shapes.add(new SceneMapper.Shape(new float[]{.08f,.34f,.3f,.31f,.3f,.6f,.08f,.55f},1,"VERTICAL  2.4 × 1.8 m"));
        shapes.add(new SceneMapper.Shape(new float[]{.62f,.44f},4,"LANDMARK · doorway"));
        for(int i=0;i<35;i++)shapes.add(new SceneMapper.Shape(new float[]{.1f+(i%7)*.12f,.35f+(i/7)*.045f},2,""));
        List<SceneMapper.MapPoint> cloud=new ArrayList<>();for(int i=0;i<100;i++)cloud.add(new SceneMapper.MapPoint(i*.05f,0,(i%11)*.2f,.8f));
        List<SceneMapper.MapSurface> surfaces=Collections.singletonList(new SceneMapper.MapSurface(new float[]{0,0,4,0,4,3,0,3},false));
        List<float[]> path=Arrays.asList(new float[]{0,0,0},new float[]{1,0,1},new float[]{2,0,1.5f});
        MapArchive.Poi poi=new MapArchive.Poi(1,1,"LANDMARK","doorway",new float[]{1,0,1,0,0,0,1});
        SceneMapper.Snapshot scene=new SceneMapper.Snapshot(shapes,1,1,350,10,.7f,cloud,surfaces,path,Collections.singletonList(poi),1);
        scene.cameraPoseValid=true;scene.cameraX=9.9f;scene.cameraZ=-.97f;scene.heading=(float)(Math.PI/3);
        hud.updateScene(scene,new float[]{.02f,-.1f,9.8f,.01f,0,-.02f,50,50},30);
        for(int i=0;i<90;i++)hud.updateScene(scene,new float[]{.02f,-.1f,9.8f+(float)Math.sin(i*.25)*2,.01f,0,-.02f,50,50},30);
        hud.toggleMap();
        hud.updateCapture(17,5);
        render(hud,"hud_synthetic",393,852);
        hud.toggleMap();
        render(hud,"map_position_cone",393,852);
        hud.toggleSensors();render(hud,"imu_synthetic",393,852);
        render(hud,"hud_small_synthetic",360,720);
    }
    @Test public void gpuOnlyShapesAreSafeDuringCanvasFallback()throws Exception {
        HudView hud=new HudView(RuntimeEnvironment.getApplication());
        hud.update(new Odometer().snapshot(0),"TRACKING","Fallback fixture",0);
        List<SceneMapper.Shape> shapes=Arrays.asList(
            new SceneMapper.Shape(new float[0],2,"",new float[]{1,2,3}),
            new SceneMapper.Shape(new float[0],3,"",new float[]{0,0,0,1,0,0}),
            new SceneMapper.Shape(new float[]{.5f},2,""),
            new SceneMapper.Shape(new float[]{0,0,1,0,1},0,""));
        hud.updateScene(new SceneMapper.Snapshot(shapes,0,0,1,0,1),new float[8],30);
        hud.setWorldGpu(false);hud.setRendererLabel("CANVAS*");
        render(hud,"canvas_fallback_transition",360,720);
    }
    @Test public void rendersSavedMapAndManualFlag()throws Exception {
        List<MapArchive.Keyframe> frames=new ArrayList<>();Random random=new Random(12);
        for(int f=0;f<20;f++) {
            float angle=f*(float)(Math.PI*2/19);float[] points=new float[240*4];
            for(int i=0;i<240;i++) {
                points[i*4]=random.nextFloat()*6-3;points[i*4+1]=random.nextFloat()*2;
                points[i*4+2]=i%2==0?-3:3;points[i*4+3]=.8f;
            }
            frames.add(new MapArchive.Keyframe(1,f,new float[]{(float)Math.cos(angle)*2,1,(float)Math.sin(angle)*2,0,0,0,1},points,new byte[0]));
        }
        MapView map=new MapView(RuntimeEnvironment.getApplication(),new MapArchive(frames));
        render(map,"map3d_synthetic",393,650);
        render(new MapView(RuntimeEnvironment.getApplication(),LoopConstraint.apply(new MapArchive(frames),1)),"map3d_manual_synthetic",393,650);
    }
}
