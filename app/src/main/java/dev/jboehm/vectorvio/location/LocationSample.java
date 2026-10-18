package dev.jboehm.vectorvio.location;

/** Raw Android location observation. Neither GNSS nor fused location is independent truth. */
public final class LocationSample {
    public enum Source { GPS_PROVIDER, GOOGLE_FUSED }
    public enum Validity { OK, INVALID, OLD_OR_FUTURE, NO_ACCURACY, IMPRECISE, MOCK }

    public final Source source;
    public final long fixElapsedNs, receiveElapsedNs, wallTimeMs;
    public final double latitude, longitude, accuracyMeters, altitudeMeters, verticalAccuracyMeters,
            speedMps, speedAccuracyMps, bearingDegrees, bearingAccuracyDegrees;
    public final boolean mock;

    public LocationSample(Source source, long fixElapsedNs, long receiveElapsedNs,
                          long wallTimeMs, double latitude, double longitude,
                          double accuracyMeters, double altitudeMeters, double verticalAccuracyMeters,
                          double speedMps, double speedAccuracyMps, double bearingDegrees,
                          double bearingAccuracyDegrees, boolean mock) {
        this.source = source; this.fixElapsedNs = fixElapsedNs;
        this.receiveElapsedNs = receiveElapsedNs; this.wallTimeMs = wallTimeMs;
        this.latitude = latitude; this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.altitudeMeters = altitudeMeters; this.verticalAccuracyMeters = verticalAccuracyMeters;
        this.speedMps = speedMps; this.speedAccuracyMps = speedAccuracyMps;
        this.bearingDegrees = bearingDegrees; this.bearingAccuracyDegrees = bearingAccuracyDegrees;
        this.mock = mock;
    }

    /** Preserve raw fixes in CSV even when they fail this conservative pairing gate. */
    public Validity validity() {
        if (source == null || fixElapsedNs <= 0 || receiveElapsedNs <= 0 ||
            !Double.isFinite(latitude) || !Double.isFinite(longitude) ||
            Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return Validity.INVALID;
        long ageNs = receiveElapsedNs - fixElapsedNs;
        if (ageNs < -500_000_000L || ageNs > 5_000_000_000L) return Validity.OLD_OR_FUTURE;
        if (mock) return Validity.MOCK;
        if (!Double.isFinite(accuracyMeters) || accuracyMeters <= 0) return Validity.NO_ACCURACY;
        if (accuracyMeters > 30) return Validity.IMPRECISE;
        return Validity.OK;
    }

    public double ageMs() { return (receiveElapsedNs - fixElapsedNs) / 1_000_000d; }

    /** Short-distance WGS84 approximation in metres; AGREEMENT only, not absolute error. */
    public static double horizontalDistanceMeters(LocationSample a, LocationSample b) {
        double latA = Math.toRadians(a.latitude), latB = Math.toRadians(b.latitude);
        double dl = latB - latA, dlon = Math.toRadians(b.longitude - a.longitude);
        double sinLat = Math.sin(dl * .5), sinLon = Math.sin(dlon * .5);
        double h = sinLat * sinLat + Math.cos(latA) * Math.cos(latB) * sinLon * sinLon;
        return 12_742_017.6 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
    }
}
