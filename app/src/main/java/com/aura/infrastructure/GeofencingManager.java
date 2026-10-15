package com.aura.infrastructure;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.ArrayList;
import java.util.List;

public class GeofencingManager {
    private static final String TAG = "GeofencingManager";
    private static final String PREF_GEOFENCE = "aura_geofence_prefs";

    private final Context context;
    private final FusedLocationProviderClient locationClient;
    private final List<SafeZone> safeZones = new ArrayList<>();
    private final SafeZoneListener listener;
    private LocationCallback locationCallback;
    private boolean isMonitoring = false;
    private Location lastRecordedLocation = null;

    public interface SafeZoneListener {
        void onExitSafeZone(String zoneName, Location location);
    }

    public interface SafeZoneCallback {
        void onResult(boolean success, SafeZone zone, String message);
    }

    public interface LocationResultCallback {
        void onLocation(Location location);
    }

    public static class SafeZone {
        public String name;
        public double latitude;
        public double longitude;
        public float radiusMeters;
        public boolean wasInside = true;

        public SafeZone(String name, double lat, double lon, float radius) {
            this.name = name;
            this.latitude = lat;
            this.longitude = lon;
            this.radiusMeters = radius;
        }
    }

    public GeofencingManager(Context context, SafeZoneListener listener) {
        this.context = context.getApplicationContext();
        this.locationClient = LocationServices.getFusedLocationProviderClient(this.context);
        this.listener = listener;
        loadPersistentSafeZones();
    }

    private synchronized void loadPersistentSafeZones() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_GEOFENCE, Context.MODE_PRIVATE);
        if (prefs.contains("safe_zone_lat") && prefs.contains("safe_zone_lon")) {
            String name = prefs.getString("safe_zone_name", "Safe Zone");
            double lat = Double.longBitsToDouble(prefs.getLong("safe_zone_lat", 0));
            double lon = Double.longBitsToDouble(prefs.getLong("safe_zone_lon", 0));
            float radius = prefs.getFloat("safe_zone_radius", 1000.0f); // default 1 km
            safeZones.clear();
            safeZones.add(new SafeZone(name, lat, lon, radius));
            Log.i(TAG, "Loaded persistent safe zone: " + name + " (radius " + radius + "m)");
        }
    }

    private synchronized void persistSafeZone(SafeZone zone) {
        if (zone == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_GEOFENCE, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("safe_zone_name", zone.name)
                .putLong("safe_zone_lat", Double.doubleToRawLongBits(zone.latitude))
                .putLong("safe_zone_lon", Double.doubleToRawLongBits(zone.longitude))
                .putFloat("safe_zone_radius", zone.radiusMeters)
                .apply();
    }

    public synchronized void addSafeZone(String name, double lat, double lon, float radius) {
        SafeZone zone = new SafeZone(name, lat, lon, radius);
        safeZones.add(zone);
        persistSafeZone(zone);
    }

    public synchronized SafeZone getPrimarySafeZone() {
        return safeZones.isEmpty() ? null : safeZones.get(0);
    }

    public synchronized Location getLastKnownLocation() {
        return lastRecordedLocation;
    }

    public synchronized boolean hasLocationPermission() {
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

    /**
     * Obtains the user's current live location asynchronously.
     */
    public void getCurrentLocation(LocationResultCallback callback) {
        if (!hasLocationPermission()) {
            if (callback != null) callback.onLocation(lastRecordedLocation);
            return;
        }

        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            lastRecordedLocation = loc;
                        }
                        if (callback != null) {
                            callback.onLocation(loc != null ? loc : lastRecordedLocation);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to get live location, falling back to last known", e);
                        if (callback != null) callback.onLocation(lastRecordedLocation);
                    });
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException getting current location", se);
            if (callback != null) callback.onLocation(lastRecordedLocation);
        }
    }

    /**
     * Marks the user's current physical position as the primary safe zone with a 1 km radius.
     */
    public void markCurrentLocationAsSafeZone(float radiusMeters, SafeZoneCallback callback) {
        if (!hasLocationPermission()) {
            if (callback != null) {
                callback.onResult(false, null, "Location permission has not been granted.");
            }
            return;
        }

        getCurrentLocation(location -> {
            if (location == null) {
                if (callback != null) {
                    callback.onResult(false, null, "Could not acquire GPS fix to mark location.");
                }
                return;
            }

            synchronized (GeofencingManager.this) {
                safeZones.clear();
                SafeZone newZone = new SafeZone("Home", location.getLatitude(), location.getLongitude(), radiusMeters);
                safeZones.add(newZone);
                persistSafeZone(newZone);
                lastRecordedLocation = location;

                // Ensure continuous monitoring is running
                if (!isMonitoring) {
                    startMonitoring();
                }

                if (callback != null) {
                    callback.onResult(true, newZone, "Safe zone marked at your current location with a 1 kilometer radius.");
                }
            }
        });
    }

    public synchronized void startMonitoring() {
        if (isMonitoring) return;

        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start geofencing: location permission not granted");
            return;
        }

        try {
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000)
                    .setMinUpdateIntervalMillis(15000)
                    .build();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    for (Location location : result.getLocations()) {
                        lastRecordedLocation = location;
                        checkSafeZones(location);
                    }
                }
            };

            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            isMonitoring = true;
            Log.i(TAG, "Geofencing location updates started");
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException starting location updates", se);
        } catch (Exception e) {
            Log.e(TAG, "Exception starting location updates", e);
        }
    }

    public synchronized void stopMonitoring() {
        if (!isMonitoring || locationCallback == null) return;
        try {
            locationClient.removeLocationUpdates(locationCallback);
        } catch (Exception ignored) {}
        isMonitoring = false;
        locationCallback = null;
    }

    private void checkSafeZones(Location location) {
        if (location == null) return;
        for (SafeZone zone : safeZones) {
            float[] distance = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(), zone.latitude, zone.longitude, distance);

            boolean isInside = distance[0] <= zone.radiusMeters;
            if (zone.wasInside && !isInside) {
                if (listener != null) listener.onExitSafeZone(zone.name, location);
            }
            zone.wasInside = isInside;
        }
    }
}
