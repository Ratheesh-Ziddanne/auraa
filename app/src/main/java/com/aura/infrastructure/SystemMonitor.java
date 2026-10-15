package com.aura.infrastructure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import androidx.core.content.ContextCompat;

public class SystemMonitor {
    private static final String TAG = "SystemMonitor";

    private final Context context;
    private final StatusListener listener;
    private final BatteryReceiver batteryReceiver;
    private boolean isRegistered = false;

    public interface StatusListener {
        void onLowBattery(int level);
    }

    public SystemMonitor(Context context, StatusListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.batteryReceiver = new BatteryReceiver();
    }

    public synchronized void start() {
        if (isRegistered) return;
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            ContextCompat.registerReceiver(context, batteryReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
            isRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register battery receiver", e);
        }
    }

    public synchronized void stop() {
        if (!isRegistered) return;
        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {}
        isRegistered = false;
    }

    public String getBatteryInfo() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return "Battery status is currently unavailable.";

        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        boolean isCharging = bm.isCharging();

        if (level < 0 || level > 100) {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (rawLevel >= 0 && scale > 0) {
                    level = (int) ((rawLevel / (float) scale) * 100);
                }
            }
        }

        return "Battery is at " + (level >= 0 ? level : "unknown") + " percent. " +
                (isCharging ? "The device is currently charging." : "It is running on battery.");
    }

    @SuppressWarnings("deprecation")
    public String getNetworkInfo() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "Network status is currently unavailable.";

            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) {
                return "You currently have no active internet connection.";
            }

            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                String ssid = (wm != null && wm.getConnectionInfo() != null) ? wm.getConnectionInfo().getSSID() : "";
                if (ssid != null && !ssid.isEmpty() && !ssid.equals("<unknown ssid>")) {
                    return "Connected to Wi-Fi network " + ssid.replace("\"", "") + ".";
                }
                return "Connected to Wi-Fi.";
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "Connected via mobile cellular data.";
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "Connected via Ethernet.";
            }
            return "Connected to network.";
        } catch (Exception e) {
            return "Unable to determine network status.";
        }
    }

    public String getStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long bytesAvailable = stat.getAvailableBytes();
            long gigaAvailable = bytesAvailable / (1024 * 1024 * 1024);
            return "You have " + gigaAvailable + " gigabytes of device storage remaining.";
        } catch (Exception e) {
            return "Device storage information is currently unavailable.";
        }
    }

    private class BatteryReceiver extends BroadcastReceiver {
        private boolean alerted15 = false;
        private boolean alerted5 = false;

        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;

            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return;

            int batteryPct = (int) (level * 100 / (float) scale);

            if (batteryPct <= 5 && !alerted5) {
                if (listener != null) listener.onLowBattery(5);
                alerted5 = true;
            } else if (batteryPct <= 15 && !alerted15) {
                if (listener != null) listener.onLowBattery(15);
                alerted15 = true;
            } else if (batteryPct > 20) {
                alerted15 = false;
                alerted5 = false;
            }
        }
    }
}
