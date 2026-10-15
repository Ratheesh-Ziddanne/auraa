package com.aura.platform;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.aura.MainActivity;
import com.aura.ai.AiKeyManager;
import com.aura.ai.ConversationMemoryManager;
import com.aura.ai.GeminiAiService;
import com.aura.core.JavaActionOrchestrator;
import com.aura.core.SafetyConfirmationManager;
import com.aura.core.VoiceInteractionController;
import com.aura.infrastructure.AuraNavigationEngine;
import com.aura.infrastructure.AuraVisionEngine;
import com.aura.infrastructure.GeofencingManager;
import com.aura.infrastructure.JavaAudioStreamer;
import com.aura.infrastructure.JavaSpeechRecognitionEngine;
import com.aura.infrastructure.JavaTextToSpeechEngine;
import com.aura.infrastructure.SafetyMonitor;
import com.aura.infrastructure.SmsAdapter;
import com.aura.infrastructure.SpectralSpeakerRecognitionEngine;
import com.aura.infrastructure.SystemMonitor;
import com.aura.service.contact.ContactResolver;
import com.aura.service.memory.PersonalMemoryService;
import com.aura.service.navigation.HouseMapRepository;
import com.aura.service.safety.GuardianAlertManager;
import com.aura.service.shortcuts.VoiceShortcutStore;
import com.aura.service.tools.AuraToolbox;

public class AuraService extends Service {
    private static final String TAG = "AuraService";
    private static final String CHANNEL_ID = "aura_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private JavaAudioStreamer streamer;
    private VoiceInteractionController controller;
    private SpectralSpeakerRecognitionEngine speakerEngine;
    private SystemMonitor systemMonitor;
    private GeofencingManager geofencingManager;
    private SafetyMonitor safetyMonitor;
    private GuardianAlertManager guardianAlertManager;

    private static AuraService instance;

    public static GuardianAlertManager getGuardianAlertManager() {
        return instance != null ? instance.guardianAlertManager : null;
    }

    public static GeofencingManager getGeofencingManager() {
        return instance != null ? instance.geofencingManager : null;
    }

    public static SpectralSpeakerRecognitionEngine getSpeakerEngine() {
        return instance != null ? instance.speakerEngine : null;
    }

    private static String currentStatus = "AURA is Ready";
    private static StatusListener statusListener;

    public interface StatusListener {
        void onStatusChanged(String status);
    }

    public static void setStatusListener(StatusListener listener) {
        statusListener = listener;
        if (listener != null) listener.onStatusChanged(currentStatus);
    }

    public static String getStatus() {
        return currentStatus;
    }

    public static void updateStatus(String status) {
        currentStatus = status;
        if (statusListener != null) {
            statusListener.onStatusChanged(status);
        }
    }

    public static final String ACTION_TRIGGER_WAKE = "com.aura.action.TRIGGER_WAKE";

    /**
     * Triggered from the UI orb tap or OS assistant session. Wakes the controller if the service is running.
     * Returns true if the service was already active and wake was triggered, false if
     * the service needs to be started first.
     */
    public static boolean triggerManualWake() {
        if (instance != null && instance.controller != null && !instance.controller.isActive()) {
            instance.controller.start();
            return true;
        }
        return false;
    }

    /**
     * Speaks an announcement directly (e.g. incoming SMS or safety notification)
     * without interrupting if already speaking or active.
     */
    public static void announceIncomingMessage(String message) {
        if (instance != null && instance.controller != null && message != null) {
            instance.controller.speak(message);
        }
    }

    public static boolean isSpeaking() {
        return instance != null && instance.controller != null && instance.controller.isSpeaking();
    }

    public static boolean interrupt() {
        if (instance != null && instance.controller != null) {
            instance.controller.interrupt();
            return true;
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();

        JavaTextToSpeechEngine tts = new JavaTextToSpeechEngine(this);
        JavaSpeechRecognitionEngine stt = new JavaSpeechRecognitionEngine(this);
        PersonalMemoryService memoryService = new PersonalMemoryService(this);
        HouseMapRepository mapRepository = new HouseMapRepository(this);
        AuraVisionEngine visionEngine = new AuraVisionEngine(this);
        AuraNavigationEngine navigationEngine = new AuraNavigationEngine(this, mapRepository);
        navigationEngine.setVisionEngine(visionEngine);
        AuraToolbox toolbox = new AuraToolbox(this);
        toolbox.setMemoryService(memoryService);
        SmsAdapter smsAdapter = new SmsAdapter(this);
        ContactResolver contactResolver = new ContactResolver(this);
        speakerEngine = new SpectralSpeakerRecognitionEngine(this);
        SafetyConfirmationManager confirmationManager = new SafetyConfirmationManager();

        systemMonitor = new SystemMonitor(this, level -> {
            if (controller != null) {
                controller.speak("Battery warning. Level is at " + level + " percent. Please charge your phone soon.");
            }
        });

        GuardianAlertManager guardianAlertManager = new GuardianAlertManager(smsAdapter, contactResolver, memoryService);
        guardianAlertManager.setSystemMonitor(systemMonitor);
        this.guardianAlertManager = guardianAlertManager;

        geofencingManager = new GeofencingManager(this, (zoneName, location) -> {
            boolean sent = guardianAlertManager.sendGeofenceAlert(zoneName, location);
            if (controller != null) {
                String feedback = "Security alert: You have moved outside the " + zoneName + " zone.";
                if (sent) {
                    feedback += " An alert message has been sent to your guardian.";
                }
                controller.speak(feedback);
            }
        });
        guardianAlertManager.setGeofencingManager(geofencingManager);

        VoiceShortcutStore shortcutStore = new VoiceShortcutStore(this);

        JavaActionOrchestrator orchestrator = new JavaActionOrchestrator(
                this,
                tts,
                systemMonitor,
                visionEngine,
                navigationEngine,
                memoryService,
                toolbox,
                smsAdapter,
                contactResolver
        );
        orchestrator.setHouseMapRepository(mapRepository);
        orchestrator.setVoiceShortcutStore(shortcutStore);
        orchestrator.setSpeakerEngine(speakerEngine);
        orchestrator.setGuardianAlertManager(guardianAlertManager);
        orchestrator.setGeofencingManager(geofencingManager);

        AiKeyManager aiKeyManager = new AiKeyManager(this);
        GeminiAiService aiService = new GeminiAiService(aiKeyManager);
        ConversationMemoryManager conversationMemory = new ConversationMemoryManager(10);

        visionEngine.setAiService(aiService);
        orchestrator.setAiService(aiService);
        orchestrator.setAiKeyManager(aiKeyManager);
        orchestrator.setConversationMemory(conversationMemory);

        streamer = new JavaAudioStreamer(this, pcm -> {
            if (controller != null && controller.isActive()) return;
            double energy = 0;
            for (short s : pcm) energy += Math.abs(s);
            if (energy / pcm.length > 5000) {
                // At first, AURA should recognize the user's voice
                boolean isFirstTime = (speakerEngine != null && !speakerEngine.isRegistered());
                if (isFirstTime) {
                    speakerEngine.register(pcm);
                    Log.i(TAG, "Recognized and enrolled user voice at first startup.");
                } else if (speakerEngine != null && !speakerEngine.verify(pcm)) {
                    Log.d(TAG, "Speech rejected: does not match enrolled user voice.");
                    return;
                }
                if (controller != null) {
                    if (isFirstTime) {
                        controller.speak("Your voice has been recognized. AURA is now locked to your voice.", () -> {
                            controller.start();
                        });
                    } else {
                        controller.start();
                    }
                }
            }
        });

        controller = new VoiceInteractionController(this, tts, stt, orchestrator, confirmationManager, streamer);
        controller.setAiService(aiService);
        controller.setConversationMemory(conversationMemory);

        safetyMonitor = new SafetyMonitor(this, (message, isUrgent) -> {
            if (isUrgent) {
                boolean sent = guardianAlertManager.sendFallAlert();
                if (sent && controller != null) {
                    controller.speak(message + " An emergency alert has been sent to your guardian.");
                    return;
                }
            }
            if (controller != null) {
                controller.speak(message);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundWithPermissions();

        // Start hardware background sensors safely if permissions are available
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            streamer.start();
        } else {
            Log.w(TAG, "AudioStreamer deferred: RECORD_AUDIO permission missing");
        }

        systemMonitor.start();
        safetyMonitor.start();

        if (geofencingManager.hasLocationPermission()) {
            geofencingManager.startMonitoring();
        } else {
            Log.w(TAG, "Geofencing deferred: location permission missing");
        }

        if (intent != null && ACTION_TRIGGER_WAKE.equals(intent.getAction())) {
            triggerManualWake();
        } else if (speakerEngine != null && !speakerEngine.isRegistered()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (controller != null && speakerEngine != null && !speakerEngine.isRegistered()) {
                    controller.startVoiceEnrollment("Hello AURA, I am here");
                }
            }, 1200);
        }

        return START_STICKY;
    }

    private void startForegroundWithPermissions() {
        Notification notification = createNotification("AURA is listening and protecting you.");

        boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                              ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        int types = 0;
        if (hasAudio) {
            types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }
        if (hasLocation) {
            types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        }

        try {
            if (types != 0) {
                startForeground(NOTIFICATION_ID, notification, types);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to startForeground: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AURA Voice Assistant Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Maintains continuous voice readiness and safety monitoring");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AURA Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (streamer != null) streamer.close();
        if (systemMonitor != null) systemMonitor.stop();
        if (safetyMonitor != null) safetyMonitor.stop();
        if (geofencingManager != null) geofencingManager.stopMonitoring();
        if (controller != null) controller.close();
        instance = null;
        super.onDestroy();
    }
}
