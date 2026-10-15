package com.aura.infrastructure;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.aura.models.ActionResult;
import com.aura.models.Platform;
import java.util.ArrayList;

public class SmsAdapter {
    private static final String TAG = "SmsAdapter";
    private final Context context;

    public SmsAdapter(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    @SuppressWarnings("deprecation")
    public ActionResult send(String phoneNumber, String body) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return new ActionResult(false, Platform.SMS, "No phone number provided.");
        }
        if (body == null || body.trim().isEmpty()) {
            return new ActionResult(false, Platform.SMS, "Message body cannot be empty.");
        }

        if (context == null) {
            return new ActionResult(false, Platform.SMS, "Device context is unavailable.");
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return new ActionResult(false, Platform.SMS, "SMS permission has not been granted.");
        }

        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager == null) {
                return new ActionResult(false, Platform.SMS, "Telephony SMS service is unavailable on this device.");
            }

            String cleanNumber = phoneNumber.trim().replaceAll("[^0-9+]", "");
            if (cleanNumber.isEmpty()) {
                return new ActionResult(false, Platform.SMS, "Invalid phone number format: " + phoneNumber);
            }

            ArrayList<String> parts = smsManager.divideMessage(body);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(cleanNumber, null, body, null, null);
            }

            Log.i(TAG, "SMS_SEND_SUCCESS");
            return new ActionResult(true, Platform.SMS, "Message successfully sent.");
        } catch (SecurityException se) {
            Log.e(TAG, "SMS_SEND_PERMISSION_DENIED");
            return new ActionResult(false, Platform.SMS, "SMS permission denied by Android security.");
        } catch (Exception e) {
            Log.e(TAG, "SMS_SEND_FAILURE");
            return new ActionResult(false, Platform.SMS, "Failed to send message: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
}
