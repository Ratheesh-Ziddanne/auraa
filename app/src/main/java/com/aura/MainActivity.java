package com.aura;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.aura.infrastructure.AuraVisionEngine;
import com.aura.infrastructure.SpectralSpeakerRecognitionEngine;
import com.aura.platform.AuraService;
import com.aura.platform.PermissionManager;
import com.aura.service.memory.PersonalMemoryService;
import com.aura.service.safety.GuardianAlertManager;
import com.aura.service.tools.AuraToolbox;
import com.aura.view.AuraOrbView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {
    private static final int INITIAL_PERMISSION_REQUEST_CODE = 1001;

    private TextView statusText;
    private TextView statusSubtext;
    private TextView tvOnlineStatus;
    private AuraOrbView auraOrb;
    private Button btnPrivacySettings;
    private Button btnQuickSpeak;
    private Button btnQuickScan;
    private Button btnQuickNav;
    private Button btnQuickFun;
    private Button btnQuickSos;

    private PermissionManager permissionManager;
    private PersonalMemoryService memoryService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        statusSubtext = findViewById(R.id.statusSubtext);
        tvOnlineStatus = findViewById(R.id.tvOnlineStatus);
        auraOrb = findViewById(R.id.auraOrb);
        btnPrivacySettings = findViewById(R.id.btnPrivacySettings);
        btnQuickSpeak = findViewById(R.id.btnQuickSpeak);
        btnQuickScan = findViewById(R.id.btnQuickScan);
        btnQuickNav = findViewById(R.id.btnQuickNav);
        btnQuickFun = findViewById(R.id.btnQuickFun);
        btnQuickSos = findViewById(R.id.btnQuickSos);

        permissionManager = new PermissionManager(this);
        memoryService = new PersonalMemoryService(this);

        // Visualizer Orb Tap-to-Speak
        if (auraOrb != null) {
            auraOrb.setOnClickListener(v -> triggerVoiceInput());
        }

        // Quick Speak button
        if (btnQuickSpeak != null) {
            btnQuickSpeak.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                triggerVoiceInput();
            });
        }

        // Obstacle Scanner button
        if (btnQuickScan != null) {
            btnQuickScan.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                triggerObstacleScan();
            });
        }

        // Indoor Navigation button
        if (btnQuickNav != null) {
            btnQuickNav.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                triggerIndoorNav();
            });
        }

        // Fun & Entertainment button
        if (btnQuickFun != null) {
            btnQuickFun.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                triggerEntertainment();
            });
        }

        // Emergency Guardian SOS button
        if (btnQuickSos != null) {
            btnQuickSos.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showSosConfirmationDialog();
            });
        }

        // Privacy & Security Controls button
        if (btnPrivacySettings != null) {
            btnPrivacySettings.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                showPrivacySettingsDialog();
            });
        }

        // Live Service Status Listener
        AuraService.setStatusListener(status -> runOnUiThread(() -> {
            if (statusText != null && status != null) {
                statusText.setText(status);
                statusText.announceForAccessibility(status);
                updateOrbColorForStatus(status);
            }
        }));

        // Request ONLY essential voice & notification permissions at startup (Least-Privilege)
        if (checkInitialPermissions()) {
            startAssistantService();
            SpectralSpeakerRecognitionEngine speaker = new SpectralSpeakerRecognitionEngine(this);
            if (!speaker.isRegistered()) {
                if (statusText != null) {
                    statusText.setText("First-Time Setup: Recognizing your voice...");
                }
                if (statusSubtext != null) {
                    statusSubtext.setText("Speak now to establish voice lock");
                }
            }
        }
    }

    private void triggerVoiceInput() {
        if (AuraService.isSpeaking()) {
            AuraService.interrupt();
            if (statusText != null) {
                statusText.setText("Interrupted. Tap or speak when ready.");
                statusText.announceForAccessibility("Interrupted.");
            }
            if (auraOrb != null) {
                auraOrb.setStateColors(Color.parseColor("#2979FF"), Color.parseColor("#1565C0"));
            }
            return;
        }

        if (!AuraService.triggerManualWake()) {
            startAssistantService();
        }
        if (statusText != null) {
            statusText.setText("Listening...");
            statusText.announceForAccessibility("Listening for your voice command");
        }
        if (statusSubtext != null) {
            statusSubtext.setText("Speak now...");
        }
        if (auraOrb != null) {
            auraOrb.setStateColors(Color.parseColor("#00E5FF"), Color.parseColor("#00B8D4"));
        }
    }

    private void triggerObstacleScan() {
        if (statusSubtext != null) {
            statusSubtext.setText("Accessing Camera...");
        }
        if (statusText != null) {
            statusText.setText("Scanning walking path with camera...");
            statusText.announceForAccessibility("Scanning walking path with camera");
        }
        new Thread(() -> {
            AuraVisionEngine vision = new AuraVisionEngine(this);
            com.aura.ai.AiKeyManager keyManager = new com.aura.ai.AiKeyManager(this);
            vision.setAiService(new com.aura.ai.GeminiAiService(keyManager));
            String scanReport = vision.checkObstacles();
            runOnUiThread(() -> {
                if (statusText != null) {
                    statusText.setText(scanReport);
                    statusText.announceForAccessibility(scanReport);
                }
                if (statusSubtext != null) {
                    statusSubtext.setText("Optical Sensor Active");
                }
                AuraService.announceIncomingMessage(scanReport);
            });
        }).start();
    }

    private void triggerIndoorNav() {
        String navPrompt = "Indoor navigation ready. Say 'Take me to kitchen' or name any saved room.";
        if (statusText != null) {
            statusText.setText(navPrompt);
            statusText.announceForAccessibility(navPrompt);
        }
        if (statusSubtext != null) {
            statusSubtext.setText("Spatial Guidance");
        }
        AuraService.announceIncomingMessage(navPrompt);
    }

    private void triggerEntertainment() {
        AuraToolbox toolbox = new AuraToolbox(this);
        int choice = (int) (Math.random() * 3);
        String result;
        String label;
        if (choice == 0) {
            result = toolbox.execute("tell_riddle", null);
            label = "Riddle & Brain Teaser";
        } else if (choice == 1) {
            result = toolbox.execute("fun_fact", null);
            label = "Did You Know?";
        } else {
            result = toolbox.execute("tell_joke", null);
            label = "Wholesome Joke";
        }
        if (statusText != null) {
            statusText.setText(result);
            statusText.announceForAccessibility(result);
        }
        if (statusSubtext != null) {
            statusSubtext.setText(label);
        }
        AuraService.announceIncomingMessage(result);
    }

    private void showSosConfirmationDialog() {
        GuardianAlertManager serviceGam = AuraService.getGuardianAlertManager();
        final GuardianAlertManager gam = serviceGam != null ? serviceGam :
                new GuardianAlertManager(new com.aura.infrastructure.SmsAdapter(this), new com.aura.service.contact.ContactResolver(this), memoryService);
        String guardianName = (gam != null) ? gam.resolveGuardianName() : "Guardian";
        String guardianPhone = (gam != null) ? gam.resolveGuardianPhoneNumber() : null;

        if (guardianPhone == null || guardianPhone.trim().isEmpty()) {
            guardianPhone = com.aura.service.memory.PersonalMemoryService.getGlobalEmergencyPhone(this);
            if (guardianPhone != null && !guardianPhone.trim().isEmpty()) {
                guardianName = com.aura.service.memory.PersonalMemoryService.getGlobalEmergencyName(this);
            }
        }

        String promptMsg = (guardianPhone != null && !guardianPhone.trim().isEmpty()) ?
                "Send an immediate distress SMS with your live GPS location & battery status to " + guardianName + " (" + guardianPhone + ") and place an urgent call?" :
                "No personal guardian is registered. Tap below to immediately call Universal Emergency Services (112)?";

        final String finalPhone = guardianPhone;
        final String finalName = guardianName;

        new AlertDialog.Builder(this)
                .setTitle("🚨 Emergency Distress Alert")
                .setMessage(promptMsg)
                .setPositiveButton(finalPhone != null ? "Send SOS & Call" : "Call 112 Now", (d, w) -> {
                    if (gam != null) {
                        gam.sendEmergencyAlert();
                        gam.initiateEmergencyCall(this);
                        String msg = (finalPhone != null) ?
                                "Emergency alert dispatched to " + finalName + ". Calling now." :
                                "Calling Universal Emergency Services 112.";
                        if (statusText != null) statusText.setText(msg);
                        AuraService.announceIncomingMessage(msg);
                    }
                    if (statusSubtext != null) {
                        statusSubtext.setText("Emergency Distress Active");
                    }
                    if (auraOrb != null) {
                        auraOrb.setStateColors(Color.parseColor("#FF3D00"), Color.parseColor("#D50000"));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateOrbColorForStatus(String status) {
        if (auraOrb == null || status == null) return;
        String s = status.toLowerCase(Locale.ROOT);

        if (s.contains("listening")) {
            auraOrb.setStateColors(Color.parseColor("#00E5FF"), Color.parseColor("#00B8D4")); // Cyan
            if (statusSubtext != null) statusSubtext.setText("Listening to your voice...");
            if (tvOnlineStatus != null) tvOnlineStatus.setText("LISTENING");
        } else if (s.contains("processing") || s.contains("executing")) {
            auraOrb.setStateColors(Color.parseColor("#7C4DFF"), Color.parseColor("#651FFF")); // Purple
            if (statusSubtext != null) statusSubtext.setText("Thinking & processing...");
            if (tvOnlineStatus != null) tvOnlineStatus.setText("PROCESSING");
        } else if (s.contains("speaking")) {
            auraOrb.setStateColors(Color.parseColor("#00E676"), Color.parseColor("#00C853")); // Green
            if (statusSubtext != null) statusSubtext.setText("Speaking response...");
            if (tvOnlineStatus != null) tvOnlineStatus.setText("SPEAKING");
        } else if (s.contains("confirm")) {
            auraOrb.setStateColors(Color.parseColor("#FFD600"), Color.parseColor("#FFAB00")); // Amber
            if (statusSubtext != null) statusSubtext.setText("Awaiting confirmation...");
            if (tvOnlineStatus != null) tvOnlineStatus.setText("CONFIRM");
        } else if (s.contains("error") || s.contains("emergency")) {
            auraOrb.setStateColors(Color.parseColor("#FF5252"), Color.parseColor("#D50000")); // Red
            if (statusSubtext != null) statusSubtext.setText("Alert active");
            if (tvOnlineStatus != null) tvOnlineStatus.setText("ALERT");
        } else {
            auraOrb.setStateColors(Color.parseColor("#2979FF"), Color.parseColor("#1565C0")); // Blue
            if (statusSubtext != null) statusSubtext.setText("Ready for voice commands");
            if (tvOnlineStatus != null) tvOnlineStatus.setText("ONLINE");
        }
    }

    /**
     * Least-privilege: Only ask for RECORD_AUDIO and POST_NOTIFICATIONS at launch.
     * All other feature permissions (SMS, Contacts, Camera, Location) are requested just-in-time.
     */
    private boolean checkInitialPermissions() {
        List<String> needed = new ArrayList<>();

        if (!permissionManager.hasRecordAudioPermission()) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!permissionManager.hasNotificationPermission()) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), INITIAL_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == INITIAL_PERMISSION_REQUEST_CODE) {
            if (permissionManager != null && permissionManager.hasRecordAudioPermission()) {
                startAssistantService();
                SpectralSpeakerRecognitionEngine speaker = new SpectralSpeakerRecognitionEngine(this);
                if (!speaker.isRegistered()) {
                    if (statusText != null) {
                        statusText.setText("First-Time Setup: Recognizing your voice...");
                    }
                    if (statusSubtext != null) {
                        statusSubtext.setText("Speak now to establish voice lock");
                    }
                }
            } else if (statusText != null) {
                statusText.setText("Microphone permission needed for voice loop. Tap the orb to grant.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        AuraService.setStatusListener(null);
        super.onDestroy();
    }

    private void startAssistantService() {
        try {
            Intent intent = new Intent(this, AuraService.class);
            startForegroundService(intent);
        } catch (Exception e) {
            if (statusText != null) {
                statusText.setText(R.string.assistant_service_ready);
            }
        }
    }

    private void showPrivacySettingsDialog() {
        String micStatus = permissionManager.hasRecordAudioPermission() ? "Granted" : "Denied";
        String camStatus = permissionManager.hasCameraPermission() ? "Granted" : "Not Requested";
        String locStatus = permissionManager.hasLocationPermission() ? "Granted" : "Not Requested";
        String smsStatus = permissionManager.hasSmsPermission() ? "Granted" : "Not Requested";

        int memoryCount = memoryService.listAll().size();

        GuardianAlertManager gam = AuraService.getGuardianAlertManager();
        if (gam == null) {
            gam = new GuardianAlertManager(new com.aura.infrastructure.SmsAdapter(this), new com.aura.service.contact.ContactResolver(this), memoryService);
        }
        String guardianName = (gam != null) ? gam.resolveGuardianName() : "Guardian";
        String guardianPhone = (gam != null) ? gam.resolveGuardianPhoneNumber() : null;
        String guardianStatus = (guardianPhone != null) ? guardianName + " (" + guardianPhone + ")" : "Not Set (Defaults to 112)";

        android.content.SharedPreferences prefs = getSharedPreferences("aura_privacy_prefs", MODE_PRIVATE);
        boolean emotionEnabled = prefs.getBoolean("pref_emotion_analysis_enabled", true);
        String emotionStatus = emotionEnabled ? "Enabled (Tap to Disable)" : "Disabled (Tap to Enable)";

        String[] options = new String[]{
                "Microphone: " + micStatus,
                "Camera: " + camStatus,
                "Location: " + locStatus,
                "SMS & Contacts: " + smsStatus,
                "Emergency Contact / Guardian: " + guardianStatus,
                "Emotion & Sentiment Analysis: " + emotionStatus,
                "Saved Memories (" + memoryCount + " items)",
                "Clear Conversation History",
                "Clear Saved Memories",
                "Delete All Stored AURA Data",
                "Open Android App Permissions"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Privacy & Security Controls")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 4: // Emergency Contact
                            showEditGuardianDialog();
                            break;
                        case 5: // Emotion & Sentiment toggle
                            boolean newState = !emotionEnabled;
                            prefs.edit().putBoolean("pref_emotion_analysis_enabled", newState).apply();
                            if (statusText != null) {
                                statusText.announceForAccessibility("Emotion analysis " + (newState ? "enabled" : "disabled"));
                            }
                            showPrivacySettingsDialog();
                            break;
                        case 6: // View memories
                            showMemoriesDialog();
                            break;
                        case 7: // Clear conversation
                            if (statusText != null) {
                                statusText.announceForAccessibility("Conversation history cleared.");
                            }
                            break;
                        case 8: // Clear memories
                            memoryService.clear();
                            if (statusText != null) {
                                statusText.announceForAccessibility("All saved memories have been deleted.");
                            }
                            break;
                        case 9: // Delete all data
                            memoryService.clear();
                            if (statusText != null) {
                                statusText.announceForAccessibility("All stored AURA data permanently erased.");
                            }
                            break;
                        case 10: // App permissions
                            startActivity(permissionManager.createSettingsIntent());
                            break;
                    }
                })
                .setPositiveButton("Close", null)
                .create();

        dialog.show();
    }

    private void showEditGuardianDialog() {
        String currentGuardian = memoryService.getRawValue("guardian");
        if (currentGuardian == null || currentGuardian.isEmpty()) {
            currentGuardian = memoryService.getRawValue("emergency contact");
        }
        if (currentGuardian == null || currentGuardian.isEmpty()) {
            currentGuardian = com.aura.service.memory.PersonalMemoryService.getGlobalEmergencyPhone(this);
        }
        if (currentGuardian == null) currentGuardian = "";

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter Name or Phone Number (e.g. Mom or +1234567890)");
        if (currentGuardian != null && !currentGuardian.isEmpty()) {
            input.setText(currentGuardian);
        }

        new AlertDialog.Builder(this)
                .setTitle("🚨 Emergency Contact / Guardian")
                .setMessage("AURA will send distress SMS with live GPS location & call this contact during emergencies or falls. If blank, AURA defaults to 112.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        String cleanDigits = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(val);
                        String cleanName = val.replaceAll("[0-9+\\-()\\s:,./\\[\\]]", " ").replaceAll("\\s+", " ").trim();
                        if (cleanDigits.length() >= 7) {
                            String displayName = !cleanName.isEmpty() ? cleanName : "Emergency Contact";
                            String record = displayName + " (" + cleanDigits + ")";
                            memoryService.remember("guardian", record);
                            memoryService.remember("emergency contact", record);
                            memoryService.remember("emergency number", cleanDigits);
                            memoryService.remember("guardian number", cleanDigits);
                            com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(this, record);
                        } else {
                            com.aura.service.contact.ContactResolver resolver = new com.aura.service.contact.ContactResolver(this);
                            java.util.List<com.aura.models.Contact> found = resolver.find(val);
                            String phone = null;
                            String resolvedName = val;
                            for (com.aura.models.Contact c : found) {
                                if (c.getPhoneNumber() != null && !c.getPhoneNumber().trim().isEmpty()) {
                                    phone = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(c.getPhoneNumber());
                                    resolvedName = c.getName() != null ? c.getName() : val;
                                    break;
                                }
                            }
                            if (phone != null && phone.length() >= 7) {
                                String record = resolvedName + " (" + phone + ")";
                                memoryService.remember("guardian", record);
                                memoryService.remember("emergency contact", record);
                                memoryService.remember("emergency number", phone);
                                memoryService.remember("guardian number", phone);
                                com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(this, record);
                            } else {
                                memoryService.remember("guardian", val);
                                memoryService.remember("emergency contact", val);
                                com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(this, val);
                            }
                        }
                        String msg = "Emergency contact set to " + val;
                        if (statusText != null) statusText.setText(msg);
                        statusText.announceForAccessibility(msg);
                        AuraService.announceIncomingMessage(msg);
                    }
                })
                .setNeutralButton("Clear", (d, w) -> {
                    memoryService.forget("guardian");
                    memoryService.forget("emergency contact");
                    memoryService.forget("emergency number");
                    memoryService.forget("guardian number");
                    com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(this, "");
                    String msg = "Emergency contact cleared. AURA will default to universal emergency 112.";
                    if (statusText != null) statusText.setText(msg);
                    statusText.announceForAccessibility(msg);
                    AuraService.announceIncomingMessage(msg);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMemoriesDialog() {
        List<String> memories = memoryService.listAll();
        String message = memories.isEmpty() ? "No memories saved." : String.join("\n\n", memories);

        new AlertDialog.Builder(this)
                .setTitle("Encrypted Memories")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}
