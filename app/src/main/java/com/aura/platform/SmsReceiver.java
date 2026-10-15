package com.aura.platform;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.aura.models.Contact;
import com.aura.service.contact.ContactResolver;
import java.util.List;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static volatile String latestMessageAnnouncement = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        try {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages == null || messages.length == 0) return;

            StringBuilder bodyBuilder = new StringBuilder();
            String senderPhone = null;

            for (SmsMessage msg : messages) {
                if (senderPhone == null) {
                    senderPhone = msg.getDisplayOriginatingAddress();
                }
                bodyBuilder.append(msg.getDisplayMessageBody());
            }

            if (senderPhone != null) {
                String senderName = senderPhone;
                ContactResolver resolver = new ContactResolver(context);
                List<Contact> contacts = resolver.find(senderPhone);
                if (!contacts.isEmpty()) {
                    senderName = contacts.get(0).getDisplayName();
                }

                String body = bodyBuilder.toString();
                String lowerBody = body.trim().toLowerCase(java.util.Locale.ROOT);

                // Auto-reply with live location if requested by the Guardian
                com.aura.service.safety.GuardianAlertManager guardianManager = AuraService.getGuardianAlertManager();
                if (guardianManager != null) {
                    String guardianPhone = guardianManager.resolveGuardianPhoneNumber();
                    if (guardianPhone != null) {
                        String cleanSender = senderPhone.replaceAll("[^0-9]", "");
                        String cleanGuardian = guardianPhone.replaceAll("[^0-9]", "");
                        boolean isGuardian = cleanSender.endsWith(cleanGuardian) || cleanGuardian.endsWith(cleanSender);

                        if (isGuardian && (lowerBody.contains("location") || lowerBody.contains("where are you") || lowerBody.contains("track") || lowerBody.equals("loc"))) {
                            guardianManager.sendLiveLocationResponse(senderPhone, null);
                            String alertText = "Your guardian requested your live location. Location coordinates have been sent.";
                            latestMessageAnnouncement = alertText;
                            AuraService.announceIncomingMessage(alertText);
                            return;
                        }
                    }
                }

                String announcement = "Incoming message from " + senderName + ": " + body;
                latestMessageAnnouncement = announcement;
                AuraService.announceIncomingMessage(announcement);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error processing incoming SMS", e);
        }
    }

    public static String getLatestMessage(Context context) {
        if (latestMessageAnnouncement != null) {
            return latestMessageAnnouncement;
        }
        if (context == null) return null;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try (Cursor cursor = context.getContentResolver().query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.BODY},
                    null, null, Telephony.Sms.Inbox.DEFAULT_SORT_ORDER + " LIMIT 1")) {
                if (cursor != null && cursor.moveToFirst()) {
                    String address = cursor.getString(0);
                    String body = cursor.getString(1);
                    return "Latest message from " + address + ": " + body;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error reading latest SMS", e);
            }
        }
        return null;
    }
}
