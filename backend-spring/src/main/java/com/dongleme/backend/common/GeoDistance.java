package com.dongleme.backend.common;

/**
 * 球面距离计算，服务端用于校验和重算轨迹距离。
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoDistance() {
    }

    public static double meters(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = radLat2 - radLat1;
        double deltaLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
