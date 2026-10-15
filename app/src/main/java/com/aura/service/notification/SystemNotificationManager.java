package com.aura.service.notification;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import com.aura.infrastructure.SystemMonitor;
import com.aura.platform.SmsReceiver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Inspects, filters, and summarizes active system and app notifications,
 * prioritizing urgent communications, alarms, calls, and system alerts.
 */
public class SystemNotificationManager {
    private static final String TAG = "SystemNotificationMgr";

    private final Context context;
    private final SystemMonitor systemMonitor;

    public static class NotificationItem {
        public final String appName;
        public final String title;
        public final String text;
        public final boolean isUrgent;
        public final String category;

        public NotificationItem(String appName, String title, String text, boolean isUrgent, String category) {
            this.appName = appName != null ? appName.trim() : "System";
            this.title = title != null ? title.trim() : "";
            this.text = text != null ? text.trim() : "";
            this.isUrgent = isUrgent;
            this.category = category != null ? category : "";
        }
    }

    public SystemNotificationManager(Context context) {
        this(context, null);
    }

    public SystemNotificationManager(Context context, SystemMonitor systemMonitor) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.systemMonitor = systemMonitor;
    }

    /**
     * Summarizes unread urgent system notifications.
     *
     * @param onlyUrgent if true, filters strictly for urgent/high-priority notifications.
     * @return Natural speech summary suitable for TTS.
     */
    public String summarizeNotifications(boolean onlyUrgent) {
        if (context == null) {
            return "You have no unread urgent notifications at this time.";
        }

        AuraNotificationListenerService listener = AuraNotificationListenerService.getInstance();
        if (listener != null) {
            return summarizeFromListener(listener, onlyUrgent);
        }

        // Listener service is not yet bound or active
        return summarizeFallback(onlyUrgent);
    }

    private String summarizeFromListener(AuraNotificationListenerService listener, boolean onlyUrgent) {
        StatusBarNotification[] sbns = listener.getActiveNotificationsSafe();
        if (sbns == null || sbns.length == 0) {
            return checkSystemAlertsFallback("You have no unread notifications at this time.");
        }

        PackageManager pm = context.getPackageManager();
        String myPkg = context.getPackageName();

        List<NotificationItem> urgentItems = new ArrayList<>();
        List<NotificationItem> normalItems = new ArrayList<>();

        for (StatusBarNotification sbn : sbns) {
            if (sbn == null) continue;
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(myPkg)) {
                // Ignore AURA's own background service notification
                continue;
            }

            Notification notif = sbn.getNotification();
            if (notif == null) continue;

            // Extract App Name
            String appName = pkg;
            try {
                if (pm != null) {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    CharSequence label = pm.getApplicationLabel(ai);
                    if (label != null && label.length() > 0) {
                        appName = label.toString();
                    }
                }
            } catch (Throwable ignored) {}

            // Extract Title & Text
            Bundle extras = notif.extras;
            String title = "";
            String text = "";
            if (extras != null) {
                CharSequence csTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
                if (csTitle != null) title = csTitle.toString();

                CharSequence csBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                if (csBigText != null && csBigText.length() > 0) {
                    text = csBigText.toString();
                } else {
                    CharSequence csText = extras.getCharSequence(Notification.EXTRA_TEXT);
                    if (csText != null) text = csText.toString();
                }
            }

            // Skip empty/silent notifications
            if (title.isEmpty() && text.isEmpty()) {
                continue;
            }

            boolean urgent = isUrgentNotification(pkg, notif, title, text);
            NotificationItem item = new NotificationItem(appName, title, text, urgent, notif.category);

            if (urgent) {
                urgentItems.add(item);
            } else if (!sbn.isOngoing()) {
                // Ignore ongoing non-urgent background tasks (like music player or downloaders) in general list
                normalItems.add(item);
            }
        }

        if (!urgentItems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("You have ").append(urgentItems.size()).append(" unread urgent notification");
            if (urgentItems.size() > 1) sb.append("s");
            sb.append(". ");

            int maxToRead = Math.min(urgentItems.size(), 3);
            for (int i = 0; i < maxToRead; i++) {
                NotificationItem item = urgentItems.get(i);
                if (i > 0) sb.append(" Next, ");
                sb.append("From ").append(item.appName).append(": ");
                if (!item.title.isEmpty()) {
                    sb.append(item.title).append(". ");
                }
                if (!item.text.isEmpty() && !item.text.equalsIgnoreCase(item.title)) {
                    sb.append(item.text).append(". ");
                }
            }

            if (urgentItems.size() > maxToRead) {
                sb.append("Plus ").append(urgentItems.size() - maxToRead).append(" more urgent alerts. ");
            }

            if (!onlyUrgent && !normalItems.isEmpty()) {
                sb.append("You also have ").append(normalItems.size()).append(" other notifications.");
            }

            return sb.toString().trim();
        }

        // No urgent items found
        if (onlyUrgent) {
            return checkSystemAlertsFallback("You have no unread urgent notifications at this time.");
        }

        if (!normalItems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("You have no urgent notifications, but you have ").append(normalItems.size()).append(" other notification");
            if (normalItems.size() > 1) sb.append("s");
            sb.append(": ");

            int count = Math.min(normalItems.size(), 3);
            for (int i = 0; i < count; i++) {
                if (i > 0) sb.append(", ");
                sb.append(normalItems.get(i).appName);
                if (!normalItems.get(i).title.isEmpty()) {
                    sb.append(" regarding ").append(normalItems.get(i).title);
                }
            }
            sb.append(".");
            return sb.toString();
        }

        return checkSystemAlertsFallback("You have no unread notifications at this time.");
    }

    @SuppressWarnings("deprecation")
    private boolean isUrgentNotification(String pkg, Notification notif, String title, String text) {
        String lowerPkg = pkg.toLowerCase(Locale.ROOT);
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        String lowerText = text.toLowerCase(Locale.ROOT);
        String combined = lowerTitle + " " + lowerText;

        // 1. Category check
        String category = notif.category;
        if (Notification.CATEGORY_CALL.equals(category) ||
            Notification.CATEGORY_ALARM.equals(category) ||
            Notification.CATEGORY_REMINDER.equals(category) ||
            Notification.CATEGORY_ERROR.equals(category) ||
            Notification.CATEGORY_EVENT.equals(category) ||
            Notification.CATEGORY_MESSAGE.equals(category)) {
            return true;
        }

        // 2. High priority check
        if (notif.priority >= Notification.PRIORITY_HIGH) {
            return true;
        }

        // 3. Urgent keywords
        if (combined.contains("urgent") || combined.contains("emergency") || combined.contains("warning") ||
            combined.contains("alert") || combined.contains("missed call") || combined.contains("calling") ||
            combined.contains("otp") || combined.contains("verification code") || combined.contains("critical") ||
            combined.contains("battery low") || combined.contains("security alert")) {
            return true;
        }

        // 4. Important messaging & communication packages
        if (lowerPkg.contains("messaging") || lowerPkg.contains("mms") || lowerPkg.contains("sms") ||
            lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") || lowerPkg.contains("dialer") ||
            lowerPkg.contains("incall") || lowerPkg.contains("signal") || lowerPkg.contains("teams")) {
            return true;
        }

        return false;
    }

    private String summarizeFallback(boolean onlyUrgent) {
        // Check for latest incoming SMS message
        String latestSms = SmsReceiver.getLatestMessage(context);
        if (latestSms != null && !latestSms.trim().isEmpty()) {
            return "You have an unread message notification. " + latestSms;
        }

        // Check hardware / connectivity system alerts
        String systemAlert = getSystemHealthAlert();
        if (systemAlert != null) {
            return "Urgent system notification: " + systemAlert;
        }

        boolean hasAccess = AuraNotificationListenerService.isNotificationAccessGranted(context);
        if (!hasAccess) {
            return "You have no unread urgent notifications. To allow AURA to announce notifications from apps like WhatsApp or Messages, please enable Notification Access for AURA in Settings.";
        }

        return "You have no unread urgent notifications at this time.";
    }

    private String checkSystemAlertsFallback(String defaultMessage) {
        String systemAlert = getSystemHealthAlert();
        if (systemAlert != null) {
            return "Urgent system alert: " + systemAlert;
        }
        return defaultMessage;
    }

    private String getSystemHealthAlert() {
        if (systemMonitor != null) {
            String batteryInfo = systemMonitor.getBatteryInfo();
            if (batteryInfo != null && (batteryInfo.contains("15 percent") || batteryInfo.contains("10 percent") ||
                batteryInfo.contains("5 percent") || batteryInfo.contains("Battery is at 1") || batteryInfo.contains("Battery is at 2") ||
                batteryInfo.contains("Battery is at 3") || batteryInfo.contains("Battery is at 4") || batteryInfo.contains("Battery is at 5"))) {
                return "Your battery is critically low. " + batteryInfo;
            }
            String networkInfo = systemMonitor.getNetworkInfo();
            if (networkInfo != null && networkInfo.contains("no active internet connection")) {
                return "No active internet connection.";
            }
        }
        return null;
    }
}
