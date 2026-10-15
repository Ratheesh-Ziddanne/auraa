package com.aura.service.safety;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.aura.infrastructure.GeofencingManager;
import com.aura.infrastructure.SmsAdapter;
import com.aura.infrastructure.SystemMonitor;
import com.aura.models.ActionResult;
import com.aura.models.Contact;
import com.aura.service.contact.ContactResolver;
import com.aura.service.memory.PersonalMemoryService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuardianAlertManager {
    private static final String TAG = "GuardianAlertManager";
    public static final String DEFAULT_EMERGENCY_SERVICE_NUMBER = "112";

    public static class EmergencyContactInfo {
        private final String name;
        private final String phoneNumber;
        private final String source;

        public EmergencyContactInfo(String name, String phoneNumber, String source) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.source = source;
        }

        public String getName() {
            return (name != null && !name.trim().isEmpty()) ? name.trim() : "Emergency Contact";
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return getName() + " (" + phoneNumber + ")";
        }
    }

    private final SmsAdapter smsAdapter;
    private final ContactResolver contactResolver;
    private final PersonalMemoryService memoryService;
    private GeofencingManager geofencingManager;
    private SystemMonitor systemMonitor;

    public GuardianAlertManager(SmsAdapter smsAdapter,
                                ContactResolver contactResolver,
                                PersonalMemoryService memoryService) {
        this.smsAdapter = smsAdapter;
        this.contactResolver = contactResolver;
        this.memoryService = memoryService;
    }

    public void setGeofencingManager(GeofencingManager geofencingManager) {
        this.geofencingManager = geofencingManager;
    }

    public void setSystemMonitor(SystemMonitor systemMonitor) {
        this.systemMonitor = systemMonitor;
    }

    /**
     * Resolves all registered emergency contacts from memory and contact book, deduplicating phone numbers.
     */
    public List<EmergencyContactInfo> resolveAllEmergencyContacts() {
        Map<String, EmergencyContactInfo> contactMap = new LinkedHashMap<>();

        // 0. Highest-priority: Dedicated global emergency contact storage
        String directPhone = PersonalMemoryService.getGlobalEmergencyPhone(null);
        String directName = PersonalMemoryService.getGlobalEmergencyName(null);
        if (directPhone != null && !directPhone.trim().isEmpty() && directPhone.length() >= 7) {
            contactMap.put(directPhone, new EmergencyContactInfo(directName, directPhone, "direct_storage"));
        }

        // 1. High-priority Encrypted Personal Memory keys
        String[] memoryKeys = new String[]{
                "guardian", "emergency contact", "emergency", "ice", "primary emergency",
                "emergency number", "guardian number", "emergency phone", "guardian phone", "caregiver"
        };
        if (memoryService != null) {
            for (String key : memoryKeys) {
                String saved = memoryService.getRawValue(key);
                if (saved != null && !saved.trim().isEmpty() && !saved.startsWith("I don't have") && !saved.startsWith("What would")) {
                    parseAndAddEmergencyContact(saved, key, contactMap);
                }
            }

            // Also inspect all stored memories for any key referencing emergency, guardian, or ice
            for (Map.Entry<String, String> entry : memoryService.getAllRawMemories().entrySet()) {
                String k = entry.getKey().toLowerCase(Locale.ROOT);
                if ((k.contains("emergency") || k.contains("guardian") || k.contains("ice")) && !contactMap.containsKey(entry.getValue())) {
                    parseAndAddEmergencyContact(entry.getValue(), entry.getKey(), contactMap);
                }
            }
        }

        // 2. Lookup standard emergency & caregiver relations in phone contacts
        if (contactResolver != null) {
            String[] contactQueries = new String[]{
                    "guardian", "emergency", "ice", "caregiver", "mom", "dad",
                    "mother", "father", "wife", "husband", "partner", "brother", "sister", "son", "daughter"
            };
            for (String query : contactQueries) {
                try {
                    List<Contact> found = contactResolver.find(query);
                    for (Contact c : found) {
                        if (c.getPhoneNumber() != null && !c.getPhoneNumber().isEmpty()) {
                            String cleanPhone = ContactResolver.sanitizePhoneNumber(c.getPhoneNumber());
                            if (cleanPhone.length() >= 7) {
                                contactMap.putIfAbsent(cleanPhone, new EmergencyContactInfo(c.getName(), cleanPhone, "contact_book"));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return new ArrayList<>(contactMap.values());
    }

    private void parseAndAddEmergencyContact(String raw, String key, Map<String, EmergencyContactInfo> contactMap) {
        if (raw == null || raw.trim().isEmpty()) return;
        String clean = raw.trim()
                .replaceFirst("(?i)^you noted (?:that |just now that |\\d+ [a-z]+ ago that )?(?:emergency contact|guardian) (?:is|are) ", "")
                .replaceAll("[.?!]+$", "")
                .trim();
        if (clean.isEmpty()) return;

        String digits = ContactResolver.sanitizePhoneNumber(clean);
        String namePart = clean.replaceAll("[0-9+\\-()\\s:,./\\[\\]]", " ").replaceAll("\\s+", " ").trim();

        if (digits.length() >= 7) {
            String contactName = !namePart.isEmpty() ? namePart : (key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1));
            if (contactResolver != null) {
                try {
                    List<Contact> found = contactResolver.find(digits);
                    if (!found.isEmpty() && found.get(0).getName() != null && !found.get(0).getName().isEmpty()) {
                        contactName = found.get(0).getName();
                    }
                } catch (Exception ignored) {}
            }
            contactMap.putIfAbsent(digits, new EmergencyContactInfo(contactName, digits, "memory"));
        } else if (contactResolver != null && !clean.isEmpty()) {
            try {
                List<Contact> found = contactResolver.find(clean);
                for (Contact c : found) {
                    if (c.getPhoneNumber() != null && !c.getPhoneNumber().isEmpty()) {
                        String cleanPhone = ContactResolver.sanitizePhoneNumber(c.getPhoneNumber());
                        if (cleanPhone.length() >= 7) {
                            contactMap.putIfAbsent(cleanPhone, new EmergencyContactInfo(c.getName(), cleanPhone, "memory_resolved"));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Resolves the primary guardian's phone number.
     */
    public String resolveGuardianPhoneNumber() {
        List<EmergencyContactInfo> list = resolveAllEmergencyContacts();
        if (!list.isEmpty()) {
            return list.get(0).getPhoneNumber();
        }
        String direct = PersonalMemoryService.getGlobalEmergencyPhone(null);
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }
        return null;
    }

    /**
     * Resolves the primary guardian's friendly name.
     */
    public String resolveGuardianName() {
        List<EmergencyContactInfo> list = resolveAllEmergencyContacts();
        if (!list.isEmpty()) {
            return list.get(0).getName();
        }
        String direct = PersonalMemoryService.getGlobalEmergencyName(null);
        if (direct != null && !direct.trim().isEmpty()) {
            return direct.trim();
        }
        return "Guardian";
    }

    private String formatMapsLink(Location location) {
        if (location == null && geofencingManager != null) {
            location = geofencingManager.getLastKnownLocation();
        }
        if (location != null) {
            String link = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", location.getLatitude(), location.getLongitude());
            if (location.hasAccuracy()) {
                link += String.format(Locale.US, " (±%.0fm)", location.getAccuracy());
            }
            return link;
        }
        return null;
    }

    private String getBatteryTelemetry() {
        if (systemMonitor != null) {
            String info = systemMonitor.getBatteryInfo();
            if (info != null && !info.contains("unavailable")) {
                return info.replaceAll("\\.$", "");
            }
        }
        return null;
    }

    /**
     * Immediately triggers an emergency phone call:
     * - Places a direct call to the Guardian if CALL_PHONE permission is held.
     * - Falls back to opening the system dialer pre-populated with Guardian's number.
     * - If NO guardian is registered, launches the system dialer with 112 (Universal Emergency).
     */
    public boolean initiateEmergencyCall(Context context) {
        if (context == null) return false;

        String phone = resolveGuardianPhoneNumber();
        boolean isServiceFallback = (phone == null || phone.isEmpty());
        String targetNumber = isServiceFallback ? DEFAULT_EMERGENCY_SERVICE_NUMBER : phone;

        Intent callIntent;
        boolean hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;

        if (hasCallPermission && !isServiceFallback) {
            callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + targetNumber));
        } else {
            callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + targetNumber));
        }

        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(callIntent);
            Log.i(TAG, "Emergency call initiated to " + targetNumber);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initiate emergency call: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends an emergency distress alert to ALL registered guardians with live location and battery info.
     */
    public boolean sendEmergencyAlert() {
        List<EmergencyContactInfo> contacts = resolveAllEmergencyContacts();
        if (contacts.isEmpty() || smsAdapter == null) {
            Log.w(TAG, "Cannot send emergency alert: No guardian contacts resolved.");
            return false;
        }

        String time = new SimpleDateFormat("h:mm a, MMM d", Locale.US).format(new Date());
        StringBuilder body = new StringBuilder("EMERGENCY ALERT from AURA: User triggered a distress alert at " + time + ".");
        
        String mapLink = formatMapsLink(null);
        if (mapLink != null) {
            body.append(" Location: ").append(mapLink).append(".");
        }
        String batt = getBatteryTelemetry();
        if (batt != null) {
            body.append(" ").append(batt).append(".");
        }
        body.append(" Please call or check on them immediately!");

        boolean anySuccess = false;
        for (EmergencyContactInfo contact : contacts) {
            ActionResult res = smsAdapter.send(contact.getPhoneNumber(), body.toString());
            Log.i(TAG, "Dispatched emergency alert to " + contact + ": success=" + res.isSuccessful());
            if (res.isSuccessful()) anySuccess = true;
        }

        return anySuccess;
    }

    /**
     * Sends an emergency fall detection alert to ALL guardians with live location and battery info.
     */
    public boolean sendFallAlert() {
        List<EmergencyContactInfo> contacts = resolveAllEmergencyContacts();
        if (contacts.isEmpty() || smsAdapter == null) {
            Log.w(TAG, "Cannot send fall alert: No guardian contacts resolved.");
            return false;
        }

        String time = new SimpleDateFormat("h:mm a, MMM d", Locale.US).format(new Date());
        StringBuilder body = new StringBuilder("EMERGENCY ALERT from AURA: Potential fall detected for user at " + time + " with no verbal response.");
        String mapLink = formatMapsLink(null);
        if (mapLink != null) {
            body.append(" Location: ").append(mapLink).append(".");
        }
        String batt = getBatteryTelemetry();
        if (batt != null) {
            body.append(" ").append(batt).append(".");
        }
        body.append(" Please check on them immediately.");

        boolean anySuccess = false;
        for (EmergencyContactInfo contact : contacts) {
            ActionResult res = smsAdapter.send(contact.getPhoneNumber(), body.toString());
            Log.i(TAG, "Dispatched fall alert to " + contact + ": success=" + res.isSuccessful());
            if (res.isSuccessful()) anySuccess = true;
        }

        return anySuccess;
    }

    /**
     * Sends a geofencing perimeter breach alert to the guardian with live location link.
     */
    public boolean sendGeofenceAlert(String zoneName, Location location) {
        String phone = resolveGuardianPhoneNumber();
        if (phone == null || smsAdapter == null) {
            Log.w(TAG, "Cannot send geofence alert: No guardian phone number resolved.");
            return false;
        }

        String time = new SimpleDateFormat("h:mm a, MMM d", Locale.US).format(new Date());
        StringBuilder body = new StringBuilder("AURA SAFETY ALERT: User has left safe zone '" + zoneName + "' at " + time + ".");
        String mapLink = formatMapsLink(location);
        if (mapLink != null) {
            body.append(" Live location: ").append(mapLink);
        }

        ActionResult res = smsAdapter.send(phone, body.toString());
        Log.i(TAG, "Dispatched geofence alert to guardian (" + phone + "): success=" + res.isSuccessful());
        return res.isSuccessful();
    }

    /**
     * Sends the current live location to the guardian (e.g. upon remote SMS query).
     */
    public boolean sendLiveLocationResponse(String targetPhone, Location location) {
        if (targetPhone == null || targetPhone.isEmpty()) {
            targetPhone = resolveGuardianPhoneNumber();
        }
        if (targetPhone == null || smsAdapter == null) return false;

        String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
        String mapLink = formatMapsLink(location);
        String body;
        if (mapLink != null) {
            body = "AURA Live Location (" + time + "): " + mapLink;
        } else {
            body = "AURA Update: Unable to acquire GPS lock at this moment.";
        }

        ActionResult res = smsAdapter.send(targetPhone, body);
        Log.i(TAG, "Sent live location to " + targetPhone + ": " + res.isSuccessful());
        return res.isSuccessful();
    }

    /**
     * Notifies the guardian that a safe zone has been marked at the user's current location.
     */
    public boolean notifySafeZoneMarked(Location location, float radiusMeters) {
        String phone = resolveGuardianPhoneNumber();
        if (phone == null || smsAdapter == null) return false;

        int km = Math.round(radiusMeters / 1000f);
        String radiusText = (km >= 1) ? km + " km" : Math.round(radiusMeters) + " m";
        StringBuilder body = new StringBuilder("AURA UPDATE: User marked their current location as a " + radiusText + " safe zone.");
        String mapLink = formatMapsLink(location);
        if (mapLink != null) {
            body.append(" Safe zone center: ").append(mapLink);
        }

        ActionResult res = smsAdapter.send(phone, body.toString());
        return res.isSuccessful();
    }

    /**
     * Sends an emotional distress or safety concern alert to the guardian with live location link.
     */
    public boolean sendEmotionalDistressAlert(String detail) {
        String phone = resolveGuardianPhoneNumber();
        if (phone == null || smsAdapter == null) {
            Log.w(TAG, "Cannot send emotional distress alert: No guardian phone number resolved.");
            return false;
        }

        String time = new SimpleDateFormat("h:mm a, MMM d", Locale.US).format(new Date());
        StringBuilder body = new StringBuilder("AURA SAFETY ALERT: High emotional distress or safety concern detected for user at " + time + ".");
        if (detail != null && !detail.trim().isEmpty()) {
            body.append(" User statement: \"").append(detail.trim()).append("\".");
        }
        String mapLink = formatMapsLink(null);
        if (mapLink != null) {
            body.append(" Live location: ").append(mapLink);
        } else {
            body.append(" Please check in with them.");
        }

        ActionResult res = smsAdapter.send(phone, body.toString());
        Log.i(TAG, "Dispatched emotional distress alert to guardian (" + phone + "): success=" + res.isSuccessful());
        return res.isSuccessful();
    }
}
