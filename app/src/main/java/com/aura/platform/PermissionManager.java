package com.aura.platform;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import java.util.Locale;

public class PermissionManager {
    private final Context context;

    public PermissionManager(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public boolean isPermissionGranted(String permission) {
        if (context == null || permission == null) return false;
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasRecordAudioPermission() {
        return isPermissionGranted(Manifest.permission.RECORD_AUDIO);
    }

    public boolean hasSmsPermission() {
        return isPermissionGranted(Manifest.permission.SEND_SMS);
    }

    public boolean hasReadSmsPermission() {
        return isPermissionGranted(Manifest.permission.READ_SMS) || isPermissionGranted(Manifest.permission.RECEIVE_SMS);
    }

    public boolean hasContactsPermission() {
        return isPermissionGranted(Manifest.permission.READ_CONTACTS);
    }

    public boolean hasCallPhonePermission() {
        return isPermissionGranted(Manifest.permission.CALL_PHONE);
    }

    public boolean hasCameraPermission() {
        return isPermissionGranted(Manifest.permission.CAMERA);
    }

    public boolean hasLocationPermission() {
        return isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS);
        }
        return true;
    }

    public boolean hasCalendarPermission() {
        return isPermissionGranted(Manifest.permission.READ_CALENDAR) &&
                isPermissionGranted(Manifest.permission.WRITE_CALENDAR);
    }

    public String getExplanationForFeature(String feature) {
        if (feature == null) return "This feature requires additional Android permissions.";
        switch (feature.toLowerCase(Locale.ROOT)) {
            case "calendar":
            case "reminders":
                return "I need calendar permission to create and check reminders on your device calendar. Please allow calendar permission in Settings.";
            case "sms":
            case "send_message":
                return "I don't have permission to send text messages. Please allow SMS permission in Android Settings.";
            case "read_sms":
            case "read_message":
                return "I need permission to read text messages to announce your latest messages. Please allow SMS permission in Settings.";
            case "contacts":
            case "call":
                return "I need permission to access your contacts to find names and phone numbers. Please allow contacts permission in Settings.";
            case "camera":
            case "vision":
                return "I need camera permission to describe your surroundings and detect obstacles. Please allow camera permission in Settings.";
            case "location":
            case "navigation":
                return "I need location permission to guide your outdoor navigation and monitor safe zones. Please allow location permission in Settings.";
            case "microphone":
            case "audio":
                return "Microphone permission is required for AURA to hear your voice commands. Please allow microphone permission in Settings.";
            default:
                return "This action requires an Android permission that is currently not granted.";
        }
    }

    public Intent createSettingsIntent() {
        if (context == null) return new Intent(Settings.ACTION_SETTINGS);
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
