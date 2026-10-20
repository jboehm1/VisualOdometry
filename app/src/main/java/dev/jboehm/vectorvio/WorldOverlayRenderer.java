package dev.jboehm.vectorvio;
import android.opengl.GLES20;
import android.opengl.Matrix;
import com.google.ar.core.Camera;
import java.nio.*;
/** GL-thread renderer. World VBOs survive camera movement; matrix refreshed each draw. */
final class WorldOverlayRenderer {
    private final WorldGeometry geometry=new WorldGeometry(),
        gpxGeometry=new WorldGeometry(.095f),estimatedGpxGeometry=new WorldGeometry(.05f);
    private final int[] buffers=new int[6],capacities=new int[6],counts=new int[6];
    private final float[] view=new float[16],projection=new float[16],vp=new float[16];
    private FloatBuffer staging;
    private int program,position,matrix,color,size,round;
    void initialize(){
        String vs="attribute vec3 aPosition;uniform mat4 uVP;uniform float uSize;void main(){gl_Position=uVP*vec4(aPosition,1.0);gl_PointSize=uSize;}";
        String fs="precision mediump float;uniform vec4 uColor;uniform float uRound;void main(){if(uRound>0.5&&distance(gl_PointCoord,vec2(0.5))>0.5)discard;gl_FragColor=uColor;}";
        int v=0,f=0;
        try{
            String version=GLES20.glGetString(GLES20.GL_VERSION);
            if(version==null||!version.contains("OpenGL ES"))throw new IllegalStateException("No OpenGL ES context");
            check("before overlay initialization");
            v=shader(GLES20.GL_VERTEX_SHADER,vs);f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            program=GLES20.glCreateProgram();if(program==0)throw new IllegalStateException("No overlay program");
            GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
            int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            position=GLES20.glGetAttribLocation(program,"aPosition");matrix=GLES20.glGetUniformLocation(program,"uVP");color=GLES20.glGetUniformLocation(program,"uColor");size=GLES20.glGetUniformLocation(program,"uSize");round=GLES20.glGetUniformLocation(program,"uRound");
            if(position<0||matrix<0||color<0||size<0||round<0)throw new IllegalStateException("Missing overlay shader binding");
            GLES20.glGenBuffers(6,buffers,0);for(int id:buffers)if(id==0)throw new IllegalStateException("No overlay buffer");
            check("overlay initialization");
        }catch(RuntimeException|OutOfMemoryError e){release();throw e;}
        finally{if(v!=0)GLES20.glDeleteShader(v);if(f!=0)GLES20.glDeleteShader(f);}
    }
    private static void check(String stage){GlErrors.check(stage,GLES20::glGetError);}
    private int shader(int type,String source){
        int s=GLES20.glCreateShader(type);if(s==0)throw new IllegalStateException("No shader");
        GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String why=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException(why);}return s;
    }
    void release(){
        try{GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);GLES20.glDisable(GLES20.GL_SCISSOR_TEST);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDepthMask(true);GLES20.glUseProgram(0);
            GLES20.glDeleteBuffers(5,buffers,0);if(program!=0)GLES20.glDeleteProgram(program);
        }finally{program=0;java.util.Arrays.fill(buffers,0);java.util.Arrays.fill(capacities,0);clear();staging=null;}
    }

    void clear(){for(int i=0;i<6;i++)counts[i]=0;}
    void update(SceneMapper.Snapshot scene){
        if(program==0)throw new IllegalStateException("Overlay is not initialized");
        check("before overlay upload");
        geometry.clear();gpxGeometry.clear();estimatedGpxGeometry.clear();
        for(SceneMapper.Shape s:scene.shapes){
            if(s.kind==5)gpxGeometry.add(3,s.world);
            else if(s.kind==7)estimatedGpxGeometry.add(3,s.world);
            else geometry.add(s.kind,s.world);
        }
        geometry.finishRoute();gpxGeometry.finishRoute();estimatedGpxGeometry.finishRoute();
        WorldGeometry.Batch[] batches={geometry.floor,geometry.wall,geometry.points,geometry.route,gpxGeometry.route,estimatedGpxGeometry.route};
        for(int i=0;i<6;i++){
            WorldGeometry.Batch b=batches[i];counts[i]=b.size/3;if(b.size==0)continue;
            if(staging==null||staging.capacity()<b.size)staging=ByteBuffer.allocateDirect(b.data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            staging.clear();staging.put(b.data,0,b.size).flip();GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffers[i]);
            if(capacities[i]<b.size){capacities[i]=b.data.length;GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,capacities[i]*4,null,GLES20.GL_DYNAMIC_DRAW);}
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,0,b.size*4,staging);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);check("overlay upload");
    }
    void draw(Camera camera,int layers,int width,int height,float density){
        if(program==0)throw new IllegalStateException("Overlay is not initialized");
        check("before overlay draw");
        camera.getViewMatrix(view,0);camera.getProjectionMatrix(projection,0,.05f,100f);Matrix.multiplyMM(vp,0,projection,0,view,0);
        GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(matrix,1,false,vp,0);GLES20.glUniform1f(size,2.4f*density);
        GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);int bottom=Math.round(166*density),top=Math.round(138*density);
        GLES20.glScissor(0,Math.min(height,bottom),width,Math.max(0,height-bottom-top));GLES20.glEnableVertexAttribArray(position);
        try{
            if(layers==0||layers==1){batch(0,.45f,.99f,.88f,.078f,false);batch(1,.71f,.63f,1,.078f,false);}
            if(layers==0||layers==2)batch(2,.97f,.84f,.54f,.44f,true);
            batch(3,1,.67f,.34f,1,false);
            batch(4,.35f,.72f,1,.90f,false);
            batch(5,.5f,.76f,1,.46f,false);
        }finally{GLES20.glDisableVertexAttribArray(position);GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);GLES20.glDisable(GLES20.GL_SCISSOR_TEST);GLES20.glDisable(GLES20.GL_BLEND);GLES20.glDepthMask(true);GLES20.glUseProgram(0);}
        check("overlay draw");
    }
    private void batch(int i,float r,float g,float b,float a,boolean point){if(counts[i]==0)return;GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,buffers[i]);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,12,0);GLES20.glUniform4f(color,r,g,b,a);GLES20.glUniform1f(round,point?1:0);GLES20.glDrawArrays(point?GLES20.GL_POINTS:GLES20.GL_TRIANGLES,0,counts[i]);}
}
