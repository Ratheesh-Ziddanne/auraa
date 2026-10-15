package com.aura.service.notification;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

/**
 * Android NotificationListenerService implementation allowing AURA to inspect
 * live system notifications and summarize unread urgent alerts.
 */
public class AuraNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "AuraNotificationListener";
    private static volatile AuraNotificationListenerService instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Log.i(TAG, "AuraNotificationListenerService connected and ready.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) {
            instance = null;
        }
        Log.i(TAG, "AuraNotificationListenerService disconnected.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    public static AuraNotificationListenerService getInstance() {
        return instance;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    /**
     * Checks if the user has enabled notification listener permission for this app in Android Settings.
     */
    public static boolean isNotificationAccessGranted(Context context) {
        if (context == null) return false;
        try {
            String pkgName = context.getPackageName();
            String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
            if (!TextUtils.isEmpty(flat)) {
                String[] names = flat.split(":");
                for (String name : names) {
                    ComponentName cn = ComponentName.unflattenFromString(name);
                    if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error checking notification listener permission", t);
        }
        return false;
    }

    /**
     * Intent to open the Android system Notification Access settings screen.
     */
    public static Intent getNotificationAccessSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Safe retrieval of active notifications from the OS.
     */
    public StatusBarNotification[] getActiveNotificationsSafe() {
        try {
            StatusBarNotification[] notifs = getActiveNotifications();
            return notifs != null ? notifs : new StatusBarNotification[0];
        } catch (Throwable t) {
            Log.w(TAG, "Error querying active notifications", t);
            return new StatusBarNotification[0];
        }
    }
}
