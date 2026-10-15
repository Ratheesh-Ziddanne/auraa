package com.aura.infrastructure;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class SafetyMonitor implements SensorEventListener {
    private static final String TAG = "SafetyMonitor";

    // Fall detection parameters
    private static final double FREE_FALL_THRESHOLD = 3.5; // m/s^2 (near zero-g during free fall)
    private static final double IMPACT_THRESHOLD = 35.0;   // m/s^2 (hard impact spike)
    private static final long COOLDOWN_MS = 15000;         // 15 seconds between impact triggers
    private static final long FREE_FALL_WINDOW_MS = 1000;  // Impact must occur within 1s of free fall

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final SafetyListener listener;
    private final Handler escalationHandler = new Handler(Looper.getMainLooper());
    private Runnable escalationRunnable;

    private boolean isRegistered = false;
    private long lastFreeFallTime = 0;
    private long lastImpactTime = 0;
    private static final long ESCALATION_DELAY_MS = 60000; // 60s no-response escalation

    public interface SafetyListener {
        void onImpactDetected(String message, boolean isUrgent);
    }

    public SafetyMonitor(Context context, SafetyListener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = (sensorManager != null) ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.listener = listener;
    }

    public synchronized void start() {
        if (isRegistered || accelerometer == null) return;
        try {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            isRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register accelerometer listener", e);
        }
    }




    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();

        // 1. Detect free-fall dip
        if (magnitude < FREE_FALL_THRESHOLD) {
            lastFreeFallTime = now;
            return;
        }

        // 2. Detect impact spike (only if in cooldown window)
        if (magnitude > IMPACT_THRESHOLD) {
            if (now - lastImpactTime > COOLDOWN_MS) {
                // Check if this impact was preceded by a free-fall dip (characteristic signature of a real fall)
                boolean precededByFreeFall = (now - lastFreeFallTime) < FREE_FALL_WINDOW_MS;
                lastImpactTime = now;

                if (listener != null) {
                    if (precededByFreeFall) {
                        listener.onImpactDetected("Warning: Potential fall detected. Are you okay? Say yes if you are safe.", true);
                        scheduleEscalation();
                    } else {
                        listener.onImpactDetected("A sudden high impact was detected. Are you okay?", false);
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /** Schedules a 60-second no-response emergency escalation after fall detection. */
    private void scheduleEscalation() {
        cancelEscalation();
        escalationRunnable = () -> {
            if (listener != null) {
                listener.onImpactDetected(
                    "Emergency alert: You have not responded after a fall. " +
                    "Alerting your emergency contacts now. " +
                    "If you are okay, say cancel.", true);
            }
        };
        escalationHandler.postDelayed(escalationRunnable, ESCALATION_DELAY_MS);
    }

    /** Cancels any pending escalation (user moved or responded). */
    public void cancelEscalation() {
        if (escalationRunnable != null) {
            escalationHandler.removeCallbacks(escalationRunnable);
            escalationRunnable = null;
        }
    }

    public synchronized void stop() {
        if (!isRegistered || sensorManager == null) return;
        try {
            sensorManager.unregisterListener(this);
        } catch (Exception ignored) {}
        isRegistered = false;
        cancelEscalation();
    }
}

