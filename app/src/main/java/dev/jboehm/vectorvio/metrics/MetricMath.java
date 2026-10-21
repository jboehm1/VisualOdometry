package dev.jboehm.vectorvio.metrics;

/** Unit conversions shared with tests. Missing data stays missing. */
public final class MetricMath {
    private MetricMath() {}
    public static double cpuCorePercent(long elapsedNs,long cpuMs) {
        return elapsedNs>0 && cpuMs>=0 ? cpuMs*100_000_000.0/elapsedNs : Double.NaN;
    }
    /** Positive current means charging in Android's API. This is whole-device power. */
    public static double batteryPowerMilliwatts(long currentMicroamps,int voltageMillivolts) {
        return currentMicroamps!=Long.MIN_VALUE && currentMicroamps!=Integer.MIN_VALUE
            && voltageMillivolts>0 ? -(double)currentMicroamps*voltageMillivolts/1_000_000.0 : Double.NaN;
    }
    public static String csv(Object value) {
        if(value==null || value instanceof Double && !Double.isFinite((Double)value)
                || value instanceof Float && !Float.isFinite((Float)value))return "";
        String s=value.toString();
        return s.indexOf(',')>=0 || s.indexOf('"')>=0 || s.indexOf('\n')>=0
            ? "\""+s.replace("\"","\"\"")+"\"" : s;
    }
}
