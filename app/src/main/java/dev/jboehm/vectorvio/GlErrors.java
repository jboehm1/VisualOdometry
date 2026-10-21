package dev.jboehm.vectorvio;
import java.util.function.IntSupplier;
/** Bounded drain: a broken driver must not keep the render thread in an error loop. */
final class GlErrors {
    static void check(String stage,IntSupplier error){
        int first=error.getAsInt();if(first==0)return;
        for(int i=0;i<7;i++)if(error.getAsInt()==0)break;
        throw new IllegalStateException(stage+" GL error 0x"+Integer.toHexString(first));
    }
}
