package dev.jboehm.vectorvio;

/** Observational UI advisory: ARCore tracking cannot mask moving bike pixels. */
final class BikeViewAdvisor {
    static String hint(boolean poseTracked,float pitchRadians,int horizontalPlanes){
        if(!poseTracked||!Float.isFinite(pitchRadians))return "";
        if(pitchRadians>-0.90f)return "";
        return horizontalPlanes>0
            ?"DOWN VIEW · avoid moving bike"
            :"DOWN VIEW · seek static ground";
    }
    private BikeViewAdvisor(){}
}
