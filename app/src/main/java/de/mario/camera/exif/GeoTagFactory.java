package de.mario.camera.exif;

import android.location.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes some geolocation exif data to the image.
 */
public class GeoTagFactory {
    static final String N = "N";
    static final String S = "S";
    static final String E = "E";
    static final String W = "W";


    public Map<ExifTag, String> create(Location location) {
        Map<ExifTag, String> tags = new HashMap<>();
        if(location != null) {
            double latitude = Math.abs(location.getLatitude());
            double longitude = Math.abs(location.getLongitude());

            int num1Lat = (int) Math.floor(latitude);
            int num2Lat = (int) Math.floor((latitude - num1Lat) * 60);
            double num3Lat = (latitude - ((double) num1Lat + ((double) num2Lat / 60))) * 3600000;

            int num1Lon = (int) Math.floor(longitude);
            int num2Lon = (int) Math.floor((longitude - num1Lon) * 60);
            double num3Lon = (longitude - ((double) num1Lon + ((double) num2Lon / 60))) * 3600000;

            String lat = num1Lat + "/1," + num2Lat + "/1," + num3Lat + "/1000";
            String lon = num1Lon + "/1," + num2Lon + "/1," + num3Lon + "/1000";

            if (location.getLatitude() > 0) {
                tags.put(ExifTag.TAG_GPS_LATITUDE_REF, N);
            } else {
                tags.put(ExifTag.TAG_GPS_LATITUDE_REF, S);
            }

            if (location.getLongitude() > 0) {
                tags.put(ExifTag.TAG_GPS_LONGITUDE_REF, E);
            } else {
                tags.put(ExifTag.TAG_GPS_LONGITUDE_REF, W);
            }

            tags.put(ExifTag.TAG_GPS_LATITUDE, lat);
            tags.put(ExifTag.TAG_GPS_LONGITUDE, lon);
        }
        return tags;
    }
}