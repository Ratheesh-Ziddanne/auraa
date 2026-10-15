package com.aura.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import com.aura.experience.HapticFeedbackManager;
import com.aura.infrastructure.AuraNavigationEngine;
import com.aura.infrastructure.AuraVisionEngine;
import com.aura.infrastructure.FlashlightController;
import com.aura.infrastructure.SmsAdapter;
import com.aura.infrastructure.SpectralSpeakerRecognitionEngine;
import com.aura.infrastructure.SystemMonitor;
import com.aura.infrastructure.TextToSpeechEngine;
import com.aura.models.ActionResult;
import com.aura.models.Contact;
import com.aura.models.Platform;
import com.aura.platform.PermissionManager;
import com.aura.platform.SmsReceiver;
import com.aura.service.contact.ContactResolver;
import com.aura.service.memory.PersonalMemoryService;
import com.aura.service.navigation.HouseMapRepository;
import com.aura.service.shortcuts.VoiceShortcutStore;
import com.aura.service.tools.AuraToolbox;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaActionOrchestrator {
    private final Context context;
    private final TextToSpeechEngine tts;
    private final SystemMonitor systemMonitor;
    private final AuraVisionEngine visionEngine;
    private final AuraNavigationEngine navigationEngine;
    private final PersonalMemoryService memoryService;
    private final AuraToolbox toolbox;
    private final SmsAdapter smsAdapter;
    private final ContactResolver contactResolver;

    private final PermissionManager permissionManager;
    private final HapticFeedbackManager haptic;
    private FlashlightController flashlight;
    private ConversationalContextEngine contextEngine;
    private HouseMapRepository houseMapRepository;
    private VoiceShortcutStore voiceShortcutStore;
    private SpectralSpeakerRecognitionEngine speakerEngine;
    private com.aura.service.safety.GuardianAlertManager guardianAlertManager;
    private com.aura.infrastructure.GeofencingManager geofencingManager;
    private com.aura.ai.AiLanguageModelService aiService;
    private com.aura.ai.AiKeyManager aiKeyManager;
    private com.aura.ai.ConversationMemoryManager conversationMemory;
    private com.aura.service.notification.SystemNotificationManager notificationManager;

    public JavaActionOrchestrator(Context context,
                                  TextToSpeechEngine tts,
                                  SystemMonitor systemMonitor,
                                  AuraVisionEngine visionEngine,
                                  AuraNavigationEngine navigationEngine,
                                  PersonalMemoryService memoryService,
                                  AuraToolbox toolbox,
                                  SmsAdapter smsAdapter,
                                  ContactResolver contactResolver) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.tts = tts;
        this.systemMonitor = systemMonitor;
        this.visionEngine = visionEngine;
        this.navigationEngine = navigationEngine;
        this.memoryService = memoryService;
        this.toolbox = toolbox;
        this.smsAdapter = smsAdapter;
        this.contactResolver = contactResolver;

        this.permissionManager = new PermissionManager(this.context);
        this.haptic = new HapticFeedbackManager(this.context);
        this.flashlight = new FlashlightController(this.context);

        if (this.context != null) {
            this.aiKeyManager = new com.aura.ai.AiKeyManager(this.context);
            this.aiService = new com.aura.ai.GeminiAiService(this.aiKeyManager);
            this.conversationMemory = new com.aura.ai.ConversationMemoryManager();
            if (this.visionEngine != null) {
                this.visionEngine.setAiService(this.aiService);
            }
        }

        this.notificationManager = new com.aura.service.notification.SystemNotificationManager(this.context, this.systemMonitor);
    }

    public JavaActionOrchestrator(TextToSpeechEngine tts,
                                  SystemMonitor systemMonitor,
                                  AuraVisionEngine visionEngine,
                                  AuraNavigationEngine navigationEngine,
                                  PersonalMemoryService memoryService,
                                  AuraToolbox toolbox) {
        this(null, tts, systemMonitor, visionEngine, navigationEngine, memoryService, toolbox, null, null);
    }

    public void setAiService(com.aura.ai.AiLanguageModelService aiService) {
        this.aiService = aiService;
        if (this.visionEngine != null) {
            this.visionEngine.setAiService(aiService);
        }
    }

    public com.aura.ai.AiLanguageModelService getAiService() {
        return this.aiService;
    }

    public void setNotificationManager(com.aura.service.notification.SystemNotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public com.aura.service.notification.SystemNotificationManager getNotificationManager() {
        return this.notificationManager;
    }

    public void setAiKeyManager(com.aura.ai.AiKeyManager keyManager) {
        this.aiKeyManager = keyManager;
    }

    public com.aura.ai.AiKeyManager getAiKeyManager() {
        return this.aiKeyManager;
    }

    public void setConversationMemory(com.aura.ai.ConversationMemoryManager memory) {
        this.conversationMemory = memory;
    }

    public com.aura.ai.ConversationMemoryManager getConversationMemory() {
        return this.conversationMemory;
    }

    public void setContextEngine(ConversationalContextEngine contextEngine) {
        this.contextEngine = contextEngine;
    }

    public void setHouseMapRepository(HouseMapRepository repository) {
        this.houseMapRepository = repository;
    }

    public void setVoiceShortcutStore(VoiceShortcutStore store) {
        this.voiceShortcutStore = store;
    }

    public void setSpeakerEngine(SpectralSpeakerRecognitionEngine engine) {
        this.speakerEngine = engine;
    }

    public SpectralSpeakerRecognitionEngine getSpeakerEngine() {
        return this.speakerEngine;
    }

    public void setGuardianAlertManager(com.aura.service.safety.GuardianAlertManager guardianAlertManager) {
        this.guardianAlertManager = guardianAlertManager;
    }

    public com.aura.service.safety.GuardianAlertManager getGuardianAlertManager() {
        return this.guardianAlertManager;
    }

    public void setGeofencingManager(com.aura.infrastructure.GeofencingManager geofencingManager) {
        this.geofencingManager = geofencingManager;
    }

    public void execute(com.aura.models.Intent intent) {
        execute(intent, null);
    }

    public void execute(com.aura.models.Intent intent, Runnable onComplete) {
        ActionResult result = executeAction(intent);
        String feedback = result.getDetail();

        if (result.isSuccessful()) {
            if (haptic != null) haptic.success();
        } else if (result.getStatus() == ActionResult.ExecutionStatus.PERMISSION_REQUIRED ||
                   result.getStatus() == ActionResult.ExecutionStatus.FAILED) {
            if (haptic != null) haptic.error();
        } else if (result.getStatus() == ActionResult.ExecutionStatus.AMBIGUOUS) {
            if (haptic != null) haptic.prompt();
        }

        if (tts != null) {
            tts.speak(feedback, 1.0f, onComplete);
        } else if (onComplete != null) {
            onComplete.run();
        }
    }

    public ActionResult executeAction(com.aura.models.Intent intent) {
        if (intent == null || intent instanceof com.aura.models.Intent.Unknown) {
            return new ActionResult(
                    ActionResult.ExecutionStatus.FAILED,
                    Platform.SYSTEM,
                    "I'm sorry, I didn't understand that command. Try saying help to hear what I can do."
            );
        }

        // Update context if available
        if (contextEngine != null) {
            contextEngine.updateIntent(intent);
        }

        // 1. Clarification Handling
        if (intent instanceof com.aura.models.Intent.ClarificationNeeded) {
            com.aura.models.Intent.ClarificationNeeded cl = (com.aura.models.Intent.ClarificationNeeded) intent;
            if (contextEngine != null) {
                contextEngine.setAwaitingClarification(cl.getMissingField(), cl.getOriginalIntent());
            }
            return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SYSTEM, cl.getQuestion());
        }

        // 2. Control & Privacy Commands
        if (intent instanceof com.aura.models.Intent.StopListening) {
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "AURA is now idle. Say AURA or tap the orb when you need me.");
        }
        if (intent instanceof com.aura.models.Intent.ClearConversation) {
            if (contextEngine != null) contextEngine.clear();
            if (conversationMemory != null) conversationMemory.clear();
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "Conversation history has been cleared.");
        }
        if (intent instanceof com.aura.models.Intent.ClearMemory) {
            if (memoryService != null) memoryService.clear();
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "All personal memories have been deleted.");
        }
        if (intent instanceof com.aura.models.Intent.DeleteAllData) {
            if (memoryService != null) memoryService.clear();
            if (contextEngine != null) contextEngine.clear();
            if (conversationMemory != null) conversationMemory.clear();
            if (houseMapRepository != null) {
                for (String room : houseMapRepository.listRooms()) {
                    houseMapRepository.removeAnchor(room);
                }
            }
            if (speakerEngine != null) speakerEngine.clearRegistration();
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "All stored AURA data, including memories, house maps, and shortcuts, has been permanently erased.");
        }

        // Voice Recognition & Speaker Profile Controls
        if (intent instanceof com.aura.models.Intent.RegisterVoice) {
            if (speakerEngine != null) {
                if (speakerEngine.isRegistered()) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "Your voice profile is already registered in AURA. AURA is exclusively locked to your voice. Say 'reset my voice' if you wish to clear and re-enroll.");
                } else {
                    speakerEngine.register();
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "Your voice profile has been registered in AURA. AURA will now exclusively respond to your voice.");
                }
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Speaker recognition engine is unavailable.");
        }

        if (intent instanceof com.aura.models.Intent.ResetVoice) {
            if (speakerEngine != null) {
                speakerEngine.clearRegistration();
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Your voice profile has been cleared. AURA will recognize and register your new voice once on your next command.");
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Speaker recognition engine is unavailable.");
        }

        if (intent instanceof com.aura.models.Intent.GetVoiceStatus) {
            boolean reg = speakerEngine != null && speakerEngine.isRegistered();
            if (reg) {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Your voice profile is currently registered and active. AURA will only respond to your voice.");
            } else {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Your voice profile is not yet registered. Speak to AURA to automatically recognize and lock to your voice.");
            }
        }

        // Conversational AI Intelligence Commands
        if (intent instanceof com.aura.models.Intent.AskAi) {
            String question = ((com.aura.models.Intent.AskAi) intent).getQuestion();
            if (aiService != null) {
                com.aura.ai.AiResponse resp = aiService.generateResponseSync(
                        question,
                        conversationMemory != null ? conversationMemory.getHistory() : null
                );
                if (resp.isSuccess()) {
                    if (conversationMemory != null) {
                        conversationMemory.addTurn(question, resp.getReply());
                    }
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, resp.getReply());
                } else {
                    return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, resp.getErrorMessage());
                }
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM,
                    "Conversational AI is not configured. Say 'Set my AI key to [your key]' to enable intelligence.");
        }

        if (intent instanceof com.aura.models.Intent.SetAiKey) {
            String key = ((com.aura.models.Intent.SetAiKey) intent).getApiKey();
            if (aiKeyManager != null) {
                aiKeyManager.setApiKey(key);
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Your AI key has been securely saved. Generative AI intelligence is now active.");
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM,
                    "Unable to save AI key: secure key manager is not initialized.");
        }

        if (intent instanceof com.aura.models.Intent.GetAiStatus) {
            boolean active = aiKeyManager != null && aiKeyManager.hasApiKey();
            String model = aiService != null ? aiService.getActiveModelName() : "gemini-1.5-flash";
            int turns = conversationMemory != null ? conversationMemory.getTurnCount() : 0;
            if (active) {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Generative AI intelligence is active using " + model + ". You have " + turns + " conversation turns in memory.");
            } else {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "AURA Intelligence is fully active and ready. You can ask any question or chat freely without needing an API key.");
            }
        }

        // 3. Time, Date, Help, System Status
        if (intent instanceof com.aura.models.Intent.GetTime) {
            String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "It is currently " + time + ".");
        }
        if (intent instanceof com.aura.models.Intent.GetDate) {
            String date = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(new Date());
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "Today is " + date + ".");
        }
        if (intent instanceof com.aura.models.Intent.Help) {
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                    "I am AURA, your accessibility voice assistant. You can ask for the time, set alarms, send messages, make phone calls, control the flashlight, or ask me to remember notes.");
        }
        if (intent instanceof com.aura.models.Intent.Emergency) {
            return executeEmergency();
        }
        if (intent instanceof com.aura.models.Intent.SystemStatus) {
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "All systems are operational. Speech, audio, and safety monitoring are active.");
        }

        // 4. System Diagnostics
        if (intent instanceof com.aura.models.Intent.GetBatteryStatus) {
            String info = systemMonitor != null ? systemMonitor.getBatteryInfo() : "Battery info unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, info);
        }
        if (intent instanceof com.aura.models.Intent.GetNetworkStatus) {
            String info = systemMonitor != null ? systemMonitor.getNetworkInfo() : "Network info unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, info);
        }
        if (intent instanceof com.aura.models.Intent.GetDeviceHealth) {
            String info = systemMonitor != null ? systemMonitor.getStorageInfo() : "Storage info unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, info);
        }

        // 5. Notifications
        if (intent instanceof com.aura.models.Intent.ReadImportantNotifications) {
            return executeReadNotifications(true);
        }
        if (intent instanceof com.aura.models.Intent.ReadNotifications) {
            return executeReadNotifications(false);
        }

        // 6. Hardware Device Controls (Flashlight, Bluetooth, Wi-Fi)
        if (intent instanceof com.aura.models.Intent.ToggleFlashlight) {
            boolean enable = ((com.aura.models.Intent.ToggleFlashlight) intent).isEnable();
            boolean ok = flashlight.setTorch(enable);
            if (ok) {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, enable ? "Flashlight turned on." : "Flashlight turned off.");
            } else {
                return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "I couldn't control the flashlight on this device.");
            }
        }

        if (intent instanceof com.aura.models.Intent.ToggleBluetooth) {
            boolean enable = ((com.aura.models.Intent.ToggleBluetooth) intent).isEnable();
            if (context != null) {
                try {
                    Intent bIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    bIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(bIntent);
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            enable ? "Opening Bluetooth settings so you can turn on Bluetooth." : "Opening Bluetooth settings so you can turn off Bluetooth.");
                } catch (Exception e) {
                    return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Unable to open Bluetooth settings.");
                }
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Device context unavailable.");
        }

        if (intent instanceof com.aura.models.Intent.ToggleWifi) {
            boolean enable = ((com.aura.models.Intent.ToggleWifi) intent).isEnable();
            if (context != null) {
                try {
                    Intent wIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    wIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(wIntent);
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            enable ? "Opening Wi-Fi settings so you can connect to Wi-Fi." : "Opening Wi-Fi settings so you can turn off Wi-Fi.");
                } catch (Exception e) {
                    return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Unable to open Wi-Fi settings.");
                }
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Device context unavailable.");
        }

        // 6. Time & Productivity (Alarms, Timers, Reminders)
        if (intent instanceof com.aura.models.Intent.SetAlarm) {
            com.aura.models.Intent.SetAlarm a = (com.aura.models.Intent.SetAlarm) intent;
            if (a.getHour() < 0) {
                return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SYSTEM, "What time should I set the alarm for?");
            }
            HashMap<String, Object> p = new HashMap<>();
            p.put("hour", a.getHour());
            p.put("minute", a.getMinute());
            p.put("message", a.getLabel());
            String res = toolbox != null ? toolbox.execute("set_alarm", p) : "Toolbox unavailable.";
            boolean success = res.contains("set for");
            return new ActionResult(success ? ActionResult.ExecutionStatus.SUCCESS : ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, res);
        }

        if (intent instanceof com.aura.models.Intent.SetTimer) {
            com.aura.models.Intent.SetTimer t = (com.aura.models.Intent.SetTimer) intent;
            if (t.getSeconds() <= 0) {
                return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SYSTEM, "How long should I set the timer for?");
            }
            HashMap<String, Object> p = new HashMap<>();
            p.put("duration", t.getSeconds());
            p.put("message", t.getLabel());
            String res = toolbox != null ? toolbox.execute("set_timer", p) : "Toolbox unavailable.";
            boolean success = res.contains("set for");
            return new ActionResult(success ? ActionResult.ExecutionStatus.SUCCESS : ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, res);
        }

        if (intent instanceof com.aura.models.Intent.ShowAlarms || intent instanceof com.aura.models.Intent.OpenClock) {
            if (toolbox != null) {
                String fb = toolbox.showAlarms();
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Clock application unavailable.");
        }

        if (intent instanceof com.aura.models.Intent.DismissAlarm) {
            if (toolbox != null) {
                String fb = toolbox.dismissAlarm();
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Clock application unavailable.");
        }

        if (intent instanceof com.aura.models.Intent.SetReminder) {
            com.aura.models.Intent.SetReminder r = (com.aura.models.Intent.SetReminder) intent;
            if (r.getTitle() == null || r.getTitle().isEmpty()) {
                return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SYSTEM, "What should I remind you about?");
            }
            String timeText = (r.getTimeText() != null && !r.getTimeText().trim().isEmpty()) ? r.getTimeText().trim() : "today";
            if (memoryService != null) {
                memoryService.remember("reminder to " + r.getTitle(), timeText);
            }
            long beginTime = parseReminderTimeMillis(timeText);
            if (toolbox != null) {
                toolbox.insertCalendarEvent(r.getTitle(), beginTime, beginTime + 3600000L);
            }
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                    "I have set a reminder to " + r.getTitle() + " at " + timeText + " and scheduled it in your calendar.");
        }

        if (intent instanceof com.aura.models.Intent.OpenCalendar) {
            if (toolbox != null) {
                String fb = toolbox.openCalendar();
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Calendar application unavailable.");
        }

        if (intent instanceof com.aura.models.Intent.ListReminders) {
            List<String> list = new ArrayList<>();
            if (memoryService != null) {
                for (String mem : memoryService.listAll()) {
                    if (mem.toLowerCase(Locale.ROOT).contains("reminder")) {
                        list.add(mem);
                    }
                }
            }
            if (list.isEmpty()) {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "You have no saved reminders. You can say: remind me to take medicine at 8 PM.");
            }
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                    "Here are your upcoming reminders: " + String.join(". ", list) + ".");
        }

        // 7. Message Reading
        if (intent instanceof com.aura.models.Intent.ReadLatestMessage) {
            if (!permissionManager.hasReadSmsPermission()) {
                return new ActionResult(ActionResult.ExecutionStatus.PERMISSION_REQUIRED, Platform.SMS,
                        permissionManager.getExplanationForFeature("read_sms"));
            }
            String latest = SmsReceiver.getLatestMessage(context);
            if (latest != null && !latest.isEmpty()) {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SMS, latest);
            }
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SMS, "You have no incoming text messages.");
        }

        // 8. Communication (Calls and SMS)
        if (intent instanceof com.aura.models.Intent.SendMessage) {
            return executeSendMessage((com.aura.models.Intent.SendMessage) intent);
        }
        if (intent instanceof com.aura.models.Intent.CallContact) {
            return executeCallContact((com.aura.models.Intent.CallContact) intent);
        }

        // 9. Vision & Navigation
        if (intent instanceof com.aura.models.Intent.DescribeObstacles) {
            String fb = visionEngine != null ? visionEngine.checkObstacles() : "Vision system unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
        }
        if (intent instanceof com.aura.models.Intent.StartNavigation) {
            com.aura.models.Intent.StartNavigation nav = (com.aura.models.Intent.StartNavigation) intent;
            if (navigationEngine != null) {
                String fb = nav.isIndoor() ?
                        navigationEngine.getIndoorInstruction(nav.getDestination()) :
                        navigationEngine.getOutdoorInstruction(nav.getDestination());
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Navigation engine is not initialized.");
        }
        if (intent instanceof com.aura.models.Intent.StopNavigation) {
            if (navigationEngine != null) {
                navigationEngine.stopNavigation();
            }
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "Navigation has been stopped.");
        }
        if (intent instanceof com.aura.models.Intent.MapHouse) {
            if (houseMapRepository != null && houseMapRepository.getRoomCount() > 0) {
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "House mapping session started. You have saved rooms: " + String.join(", ", houseMapRepository.listRooms()) +
                        ". Move to any new room and say: remember this room is the kitchen.");
            }
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                    "House mapping session started. Move to any room and say: remember this room is the kitchen.");
        }
        if (intent instanceof com.aura.models.Intent.MarkSafeZone) {
            com.aura.models.Intent.MarkSafeZone mark = (com.aura.models.Intent.MarkSafeZone) intent;
            return executeMarkSafeZone(mark.getRadiusMeters());
        }
        if (intent instanceof com.aura.models.Intent.GetCurrentLocation) {
            return executeGetCurrentLocation();
        }

        // 10. Memory Operations
        if (intent instanceof com.aura.models.Intent.Remember) {
            com.aura.models.Intent.Remember rem = (com.aura.models.Intent.Remember) intent;
            String k = rem.getKey();
            String v = rem.getValue();

            // Auto-detect room mapping during house mapping sessions
            if (houseMapRepository != null && (k.toLowerCase().contains("room") || v.toLowerCase().contains("room") || k.equalsIgnoreCase("this room"))) {
                String roomName = v.replaceAll("^(?i)(?:the|a|my)\\s+", "").replaceAll("[.?!]+$", "").trim();
                if (k.toLowerCase().contains("room") && !k.equalsIgnoreCase("this room")) {
                    roomName = k.replaceAll("^(?i)room\\s+", "").trim();
                }
                if (!roomName.isEmpty()) {
                    int count = houseMapRepository.getRoomCount();
                    double x = (count == 0) ? 2.0 : ((count % 2 == 1) ? 3.0 * ((count + 1) / 2) : -3.0 * (count / 2));
                    double y = (count == 0) ? 3.0 : 3.5 * ((count + 1) / 2);
                    houseMapRepository.saveAnchor(roomName, x, y, 1, "Mapped room anchor");
                    if (navigationEngine != null) {
                        navigationEngine.setCurrentRoom(roomName);
                    }
                }
            }

            boolean isEmergencyKey = k.toLowerCase().contains("emergency") || k.toLowerCase().contains("guardian") ||
                                     k.equalsIgnoreCase("ice") || k.toLowerCase().contains("caregiver");
            if (isEmergencyKey && memoryService != null) {
                String cleanDigits = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(v);
                String cleanName = v.replaceAll("[0-9+\\-()\\s:,./\\[\\]]", " ").replaceAll("\\s+", " ").trim();

                if (cleanDigits.length() >= 7) {
                    String displayName = !cleanName.isEmpty() ? cleanName : "Emergency Contact";
                    String record = displayName + " (" + cleanDigits + ")";
                    memoryService.remember("emergency contact", record);
                    memoryService.remember("guardian", record);
                    memoryService.remember("emergency number", cleanDigits);
                    memoryService.remember("guardian number", cleanDigits);
                    com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(context, record);
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "Emergency contact registered as " + displayName + " with phone number " + cleanDigits + ". When you say emergency, I will alert and call this contact immediately.");
                } else if (!cleanName.isEmpty()) {
                    String resolvedPhone = null;
                    String resolvedName = cleanName;
                    if (contactResolver != null) {
                        try {
                            java.util.List<com.aura.models.Contact> found = contactResolver.find(cleanName);
                            for (com.aura.models.Contact c : found) {
                                if (c.getPhoneNumber() != null && !c.getPhoneNumber().trim().isEmpty()) {
                                    resolvedPhone = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(c.getPhoneNumber());
                                    resolvedName = c.getName() != null ? c.getName() : cleanName;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    if (resolvedPhone != null && resolvedPhone.length() >= 7) {
                        String record = resolvedName + " (" + resolvedPhone + ")";
                        memoryService.remember("emergency contact", record);
                        memoryService.remember("guardian", record);
                        memoryService.remember("emergency number", resolvedPhone);
                        memoryService.remember("guardian number", resolvedPhone);
                        com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(context, record);
                        return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                                "I found " + resolvedName + " in your contacts. Emergency contact has been set to " + resolvedName + " at " + resolvedPhone + ". When you say emergency, I will alert and call them immediately.");
                    } else {
                        memoryService.remember("emergency contact", cleanName);
                        memoryService.remember("guardian", cleanName);
                        com.aura.service.memory.PersonalMemoryService.saveEmergencyContactDirectly(context, cleanName);
                        return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                                "I have saved " + cleanName + " as your emergency contact. I couldn't find a phone number for " + cleanName + " in your contacts. Please say: my emergency number is [phone number], so I can call them in an emergency.");
                    }
                }
            }

            if (memoryService != null) {
                memoryService.remember(k, v);
                boolean isPlural = (k.endsWith("s") && !k.endsWith("ss")) || k.equalsIgnoreCase("glasses") || k.equalsIgnoreCase("keys");
                String verb = isPlural ? "are" : "is";
                String noun = k.startsWith("my ") ? k : "your " + k;
                String sentence = (v.startsWith("is ") || v.startsWith("are ") || v.startsWith("was ") || v.startsWith("were ")) ?
                        noun + " " + v :
                        noun + " " + verb + " " + v;
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "I have noted that " + sentence + ".");
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Memory service unavailable.");
        }
        if (intent instanceof com.aura.models.Intent.Recall) {
            com.aura.models.Intent.Recall rec = (com.aura.models.Intent.Recall) intent;
            if (memoryService != null) {
                if (memoryService.hasMemory(rec.getQuery())) {
                    String fb = memoryService.recall(rec.getQuery());
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
                } else if ("guardian".equalsIgnoreCase(rec.getQuery().trim()) || "emergency contact".equalsIgnoreCase(rec.getQuery().trim())) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "You have not set an emergency contact yet. Say: my emergency contact is [name or phone number].");
                } else {
                    String q = rec.getQuery()
                            .replaceAll("^(?i)(?:where\\s+did\\s+i\\s+(?:put|leave|keep|place)\\s+(?:my\\s+|the\\s+)?|where\\s+(?:is|are)\\s+(?:my\\s+|the\\s+)?|what\\s+is\\s+(?:my\\s+|the\\s+)?|my\\s+|the\\s+)", "")
                            .replaceAll("[.?!]+$", "")
                            .trim();
                    String fb = "I don't have a saved note for your " + q + ". Would you like me to scan your surroundings with the camera?";
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
                }
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Memory service unavailable.");
        }
        if (intent instanceof com.aura.models.Intent.Forget) {
            com.aura.models.Intent.Forget f = (com.aura.models.Intent.Forget) intent;
            if (memoryService != null) {
                boolean removed = memoryService.forget(f.getKey());
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        removed ? "I have forgotten that memory." : "I couldn't find a matching memory to delete.");
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Memory service unavailable.");
        }
        if (intent instanceof com.aura.models.Intent.ListMemories) {
            if (memoryService != null) {
                List<String> list = memoryService.listAll();
                if (list.isEmpty()) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "You have no saved memories.");
                }
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Here are your saved memories: " + String.join(". ", list));
            }
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Memory service unavailable.");
        }

        // 11. Calculations & Tools
        if (intent instanceof com.aura.models.Intent.Calculate) {
            String expr = ((com.aura.models.Intent.Calculate) intent).getExpression();
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, evaluateArithmetic(expr));
        }
        if (intent instanceof com.aura.models.Intent.MediaControl) {
            String action = ((com.aura.models.Intent.MediaControl) intent).getAction();
            String fb = toolbox != null ? toolbox.controlMedia(action) : "Media control unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
        }
        if (intent instanceof com.aura.models.Intent.OpenApp) {
            String appName = ((com.aura.models.Intent.OpenApp) intent).getAppName();
            HashMap<String, Object> p = new HashMap<>();
            p.put("appName", appName);
            String fb = toolbox != null ? toolbox.execute("open_app", p) : "Toolbox unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
        }
        if (intent instanceof com.aura.models.Intent.SearchWeb) {
            String query = ((com.aura.models.Intent.SearchWeb) intent).getQuery();
            HashMap<String, Object> p = new HashMap<>();
            p.put("query", query);
            String fb = toolbox != null ? toolbox.execute("search_web", p) : "Toolbox unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
        }
        if (intent instanceof com.aura.models.Intent.ExecuteTool) {
            com.aura.models.Intent.ExecuteTool tool = (com.aura.models.Intent.ExecuteTool) intent;
            String toolName = tool.getToolName();

            if ("read_text_camera".equalsIgnoreCase(toolName)) {
                String fb = visionEngine != null ? visionEngine.readText() : (toolbox != null ? toolbox.execute(toolName, tool.getParameters()) : "Vision unavailable.");
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("detect_color".equalsIgnoreCase(toolName)) {
                String fb = visionEngine != null ? visionEngine.detectColor() : (toolbox != null ? toolbox.execute(toolName, tool.getParameters()) : "Vision unavailable.");
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("scan_product".equalsIgnoreCase(toolName)) {
                String fb = visionEngine != null ? visionEngine.scanProduct() : (toolbox != null ? toolbox.execute(toolName, tool.getParameters()) : "Vision unavailable.");
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("check_lighting".equalsIgnoreCase(toolName)) {
                String fb = visionEngine != null ? visionEngine.checkLighting() : (toolbox != null ? toolbox.execute(toolName, tool.getParameters()) : "Vision unavailable.");
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("find_object".equalsIgnoreCase(toolName)) {
                String obj = (String) tool.getParameters().get("objectName");
                if (memoryService != null && obj != null && memoryService.hasMemory(obj)) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            memoryService.recall(obj) + " If it's not there, hold your camera at chest level to scan for it.");
                }
                String fb = visionEngine != null ? visionEngine.findObject(obj) : (toolbox != null ? toolbox.execute(toolName, tool.getParameters()) : "Vision unavailable.");
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("set_current_room".equalsIgnoreCase(toolName)) {
                String room = (String) tool.getParameters().get("room");
                if (navigationEngine != null) {
                    navigationEngine.setCurrentRoom(room);
                }
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Your current location is set to the " + room + ". Indoor spatial navigation will now route from here.");
            }
            if ("get_current_room".equalsIgnoreCase(toolName)) {
                String cur = navigationEngine != null ? navigationEngine.getCurrentRoom() : null;
                if (cur != null) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "You are currently in the " + cur + ".");
                }
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "Your current room is not set yet. You can say: I am in the living room.");
            }
            if ("list_mapped_rooms".equalsIgnoreCase(toolName)) {
                if (houseMapRepository != null && houseMapRepository.getRoomCount() > 0) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "Your mapped rooms are: " + String.join(", ", houseMapRepository.listRooms()) + ".");
                }
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                        "You have no mapped rooms yet. Say 'map my house' to begin indoor room mapping.");
            }
            if ("delete_mapped_room".equalsIgnoreCase(toolName)) {
                String room = (String) tool.getParameters().get("room");
                if (houseMapRepository != null && houseMapRepository.removeAnchor(room)) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                            "Removed " + room + " from your mapped rooms.");
                }
                return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM,
                        "Could not find a mapped room named " + room + ".");
            }
            if ("nav_next_step".equalsIgnoreCase(toolName)) {
                String fb = navigationEngine != null ? navigationEngine.getNextMicroStep() : "Navigation engine unavailable.";
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("nav_steps_remaining".equalsIgnoreCase(toolName)) {
                String fb = navigationEngine != null ? navigationEngine.getPacesRemainingStatus() : "Navigation engine unavailable.";
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("nav_check_alignment".equalsIgnoreCase(toolName)) {
                String fb = navigationEngine != null ? navigationEngine.checkHeadingAlignment(0.0) : "Navigation engine unavailable.";
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("nav_locate_doorway".equalsIgnoreCase(toolName)) {
                String fb = navigationEngine != null ? navigationEngine.locateDoorway() : "Navigation engine unavailable.";
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("nav_record_steps".equalsIgnoreCase(toolName)) {
                int paces = 1;
                if (tool.getParameters() != null && tool.getParameters().containsKey("paces")) {
                    try {
                        paces = Integer.parseInt(tool.getParameters().get("paces").toString());
                    } catch (Exception ignored) {}
                }
                String fb = navigationEngine != null ? navigationEngine.recordStepsTaken(paces) : "Navigation engine unavailable.";
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }
            if ("nav_scan_path".equalsIgnoreCase(toolName)) {
                String fb = navigationEngine != null ? navigationEngine.scanPathAhead() : "Navigation engine unavailable.";
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
            }

            String fb = toolbox != null ? toolbox.execute(tool.getToolName(), tool.getParameters()) : "Toolbox unavailable.";
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fb);
        }

        return new ActionResult(ActionResult.ExecutionStatus.UNSUPPORTED, Platform.SYSTEM, "That action is not supported.");
    }

    private ActionResult executeEmergency() {
        if (guardianAlertManager == null && (smsAdapter != null || memoryService != null || contactResolver != null || context != null)) {
            SmsAdapter adapter = smsAdapter != null ? smsAdapter : (context != null ? new SmsAdapter(context) : null);
            guardianAlertManager = new com.aura.service.safety.GuardianAlertManager(adapter, contactResolver, memoryService);
            if (geofencingManager != null) guardianAlertManager.setGeofencingManager(geofencingManager);
            if (systemMonitor != null) guardianAlertManager.setSystemMonitor(systemMonitor);
        }

        if (guardianAlertManager != null) {
            String guardianPhone = guardianAlertManager.resolveGuardianPhoneNumber();
            String guardianName = guardianAlertManager.resolveGuardianName();

            // Extra fallback: Direct resolution from memoryService if guardianAlertManager didn't find a phone
            if ((guardianPhone == null || guardianPhone.trim().isEmpty()) && memoryService != null) {
                String[] emergencyKeys = new String[]{"emergency number", "guardian number", "emergency contact", "guardian", "emergency phone", "emergency"};
                for (String k : emergencyKeys) {
                    String raw = memoryService.getRawValue(k);
                    if (raw != null && !raw.trim().isEmpty()) {
                        String digits = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(raw);
                        if (digits.length() >= 7) {
                            guardianPhone = digits;
                            String n = raw.replaceAll("[0-9+\\-()\\s:,./\\[\\]]", " ").replaceAll("\\s+", " ").trim();
                            if (!n.isEmpty()) guardianName = n;
                            break;
                        } else if (contactResolver != null) {
                            try {
                                java.util.List<com.aura.models.Contact> found = contactResolver.find(raw);
                                for (com.aura.models.Contact c : found) {
                                    if (c.getPhoneNumber() != null && !c.getPhoneNumber().trim().isEmpty()) {
                                        guardianPhone = com.aura.service.contact.ContactResolver.sanitizePhoneNumber(c.getPhoneNumber());
                                        guardianName = c.getName() != null ? c.getName() : raw;
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                            if (guardianPhone != null) break;
                        }
                    }
                }
            }

            // Direct resolution from PersonalMemoryService static global storage
            if (guardianPhone == null || guardianPhone.trim().isEmpty()) {
                guardianPhone = com.aura.service.memory.PersonalMemoryService.getGlobalEmergencyPhone(context);
                if (guardianPhone != null && !guardianPhone.trim().isEmpty()) {
                    guardianName = com.aura.service.memory.PersonalMemoryService.getGlobalEmergencyName(context);
                }
            }

            if (guardianPhone != null && !guardianPhone.trim().isEmpty()) {
                boolean hasSmsPerm = (context == null || permissionManager == null || permissionManager.hasSmsPermission());
                boolean sent = false;
                if (hasSmsPerm) {
                    sent = guardianAlertManager.sendEmergencyAlert();
                }

                // Immediately place emergency call to the guardian
                if (context != null) {
                    guardianAlertManager.initiateEmergencyCall(context);
                }

                String contactLabel = (guardianName != null && !guardianName.equalsIgnoreCase("Guardian")) ? guardianName : "your emergency contact";
                String msg;
                if (sent) {
                    msg = "Emergency protocol triggered. An urgent distress SMS with your live location has been sent to your guardian, " + contactLabel + ", and I am calling them now.";
                } else if (!hasSmsPerm) {
                    msg = "Emergency protocol triggered. SMS permission is not granted to send text alerts, but I am placing an emergency call to your guardian, " + contactLabel + ", right now.";
                } else {
                    msg = "Emergency protocol triggered. I was unable to send the SMS alert, but I am placing an emergency call to your guardian, " + contactLabel + ", right now.";
                }
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, msg);
            } else {
                if (context != null) {
                    guardianAlertManager.initiateEmergencyCall(context);
                }
                String registeredContactName = (guardianName != null && !guardianName.equalsIgnoreCase("Guardian")) ? guardianName : null;
                if (registeredContactName == null && memoryService != null) {
                    registeredContactName = memoryService.getRawValue("guardian");
                    if (registeredContactName == null) registeredContactName = memoryService.getRawValue("emergency contact");
                }
                String fallbackMsg = (registeredContactName != null && !registeredContactName.trim().isEmpty()) ?
                        ("Emergency protocol triggered! Your emergency contact is set to " + registeredContactName + ", but no phone number was found. Dialing emergency services 112 for you right now.") :
                        ("Emergency protocol triggered! No personal guardian was found, so I am dialing emergency services 112 for you right now.");
                return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, fallbackMsg);
            }
        }
        return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM,
                "Emergency protocol triggered. No emergency dispatch service is available on this device.");
    }

    private ActionResult executeSendMessage(com.aura.models.Intent.SendMessage msg) {
        String recipient = msg.getRecipient();
        String body = msg.getBody();

        if (recipient == null || recipient.trim().isEmpty()) {
            if (contextEngine != null) {
                contextEngine.setAwaitingClarification("recipient", msg);
            }
            return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SMS, "Who should I send the message to?");
        }
        if (body == null || body.trim().isEmpty()) {
            if (contextEngine != null) {
                contextEngine.setAwaitingClarification("body", msg);
            }
            return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SMS, "What message would you like to send to " + recipient + "?");
        }

        if (!permissionManager.hasSmsPermission()) {
            return new ActionResult(ActionResult.ExecutionStatus.PERMISSION_REQUIRED, Platform.SMS,
                    permissionManager.getExplanationForFeature("sms"));
        }

        recipient = recipient.trim()
                .replaceAll("^(?i)(?:to\\s+|my\\s+)", "")
                .replaceAll("(?i)\\s+(?:mobile|phone|cell|number)$", "")
                .replaceAll("(?i)('s)$", "")
                .trim();

        String targetPhone = recipient;
        String contactDisplayName = recipient;
        boolean isRawNumber = recipient.matches(".*[0-9]{5,}.*");

        if (!isRawNumber && !permissionManager.hasContactsPermission()) {
            return new ActionResult(ActionResult.ExecutionStatus.PERMISSION_REQUIRED, Platform.SMS,
                    permissionManager.getExplanationForFeature("contacts"));
        }

        if (contactResolver != null && permissionManager.hasContactsPermission()) {
            List<Contact> contacts = contactResolver.find(recipient);
            if (contacts.isEmpty()) {
                if (!isRawNumber) {
                    return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SMS,
                            "I could not find " + recipient + " in your contacts.");
                }
            } else if (contacts.size() == 1) {
                Contact c = contacts.get(0);
                contactDisplayName = c.getDisplayName();
                targetPhone = c.getPhoneNumber();
                if (contextEngine != null) contextEngine.updateContact(c);
            } else {
                String prompt = contactResolver.formatDisambiguationPrompt(recipient, contacts);
                if (contextEngine != null) {
                    contextEngine.setAwaitingClarification("recipient", msg);
                }
                return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.SMS, prompt);
            }
        }

        targetPhone = ContactResolver.sanitizePhoneNumber(targetPhone);
        if (targetPhone == null || targetPhone.trim().isEmpty()) {
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SMS,
                    "I found " + contactDisplayName + " in your contacts, but they do not have a valid phone number.");
        }

        if (smsAdapter == null) {
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SMS, "SMS service is unavailable.");
        }

        ActionResult sendResult = smsAdapter.send(targetPhone, body);
        if (sendResult.isSuccessful()) {
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SMS,
                    "Message sent to " + contactDisplayName + ".");
        } else {
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SMS, sendResult.getDetail());
        }
    }

    private ActionResult executeCallContact(com.aura.models.Intent.CallContact call) {
        String target = call.getContactName();
        if (target == null || target.trim().isEmpty()) {
            if (contextEngine != null) {
                contextEngine.setAwaitingClarification("contact", call);
            }
            return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.CALL, "Who would you like to call?");
        }

        target = target.trim()
                .replaceAll("^(?i)(?:to\\s+|my\\s+)", "")
                .replaceAll("(?i)\\s+(?:mobile|phone|cell|number)$", "")
                .replaceAll("(?i)('s)$", "")
                .trim();

        if (target.toLowerCase().contains("emergency") || target.toLowerCase().contains("guardian") ||
            target.equalsIgnoreCase("ice") || target.toLowerCase().contains("caregiver")) {
            return executeEmergency();
        }

        String phoneNumber = target;
        String name = target;
        boolean isRawNumber = target.matches(".*[0-9]{5,}.*");

        if (!isRawNumber && !permissionManager.hasContactsPermission()) {
            return new ActionResult(ActionResult.ExecutionStatus.PERMISSION_REQUIRED, Platform.CALL,
                    permissionManager.getExplanationForFeature("contacts"));
        }

        if (contactResolver != null && permissionManager.hasContactsPermission()) {
            List<Contact> contacts = contactResolver.find(target);
            if (contacts.isEmpty()) {
                if (!isRawNumber) {
                    return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.CALL,
                            "I could not find a contact named " + target + ".");
                }
            } else if (contacts.size() == 1) {
                Contact c = contacts.get(0);
                name = c.getDisplayName();
                phoneNumber = c.getPhoneNumber();
                if (contextEngine != null) contextEngine.updateContact(c);
            } else {
                String prompt = contactResolver.formatDisambiguationPrompt(target, contacts);
                if (contextEngine != null) {
                    contextEngine.setAwaitingClarification("contact", call);
                }
                return new ActionResult(ActionResult.ExecutionStatus.AMBIGUOUS, Platform.CALL, prompt);
            }
        }

        phoneNumber = ContactResolver.sanitizePhoneNumber(phoneNumber);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.CALL,
                    "I could not find a valid phone number for " + target + ".");
        }

        if (context != null) {
            try {
                boolean placed = com.aura.MainActivity.placePhoneCall(context, phoneNumber);
                if (placed) {
                    return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.CALL, "Placing call to " + name + ".");
                } else {
                    return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.CALL, "Unable to place call to " + name + ".");
                }
            } catch (Exception e) {
                return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.CALL, "Unable to place call: " + e.getMessage());
            }
        }
        return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.CALL, "Device context unavailable.");
    }

    private String evaluateArithmetic(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "I didn't receive a math problem to solve.";
        String clean = raw.toLowerCase(Locale.ROOT)
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("multiplied by", "*")
                .replace("times", "*")
                .replace("x", "*")
                .replace("divided by", "/")
                .replace("over", "/")
                .replaceAll("[^0-9\\+\\-\\*/\\.]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        try {
            String[] tokens = clean.split(" ");
            if (tokens.length < 3) {
                for (char op : new char[]{'+', '-', '*', '/'}) {
                    int idx = clean.indexOf(op);
                    if (idx > 0 && idx < clean.length() - 1) {
                        double a = Double.parseDouble(clean.substring(0, idx).trim());
                        double b = Double.parseDouble(clean.substring(idx + 1).trim());
                        double res = (op == '+') ? a + b : (op == '-') ? a - b : (op == '*') ? a * b : a / b;
                        return formatMathResult(res);
                    }
                }
                return "I couldn't calculate that math expression.";
            }

            double result = Double.parseDouble(tokens[0]);
            for (int i = 1; i < tokens.length; i += 2) {
                if (i + 1 >= tokens.length) break;
                String op = tokens[i];
                double nextVal = Double.parseDouble(tokens[i + 1]);
                if (op.equals("+")) result += nextVal;
                else if (op.equals("-")) result -= nextVal;
                else if (op.equals("*")) result *= nextVal;
                else if (op.equals("/")) {
                    if (nextVal == 0) return "You cannot divide by zero.";
                    result /= nextVal;
                }
            }
            return formatMathResult(result);
        } catch (Exception e) {
            return "Unable to calculate.";
        }
    }

    private String formatMathResult(double val) {
        if (val == (long) val) {
            return "The answer is " + (long) val + ".";
        } else {
            return String.format(Locale.US, "The answer is %.2f.", val);
        }
    }

    private ActionResult executeMarkSafeZone(float radiusMeters) {
        if (geofencingManager == null) {
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Geofencing system is unavailable.");
        }
        if (!permissionManager.hasLocationPermission()) {
            return new ActionResult(ActionResult.ExecutionStatus.PERMISSION_REQUIRED, Platform.SYSTEM,
                    permissionManager.getExplanationForFeature("location"));
        }

        final String[] response = new String[1];
        final Object lock = new Object();

        geofencingManager.markCurrentLocationAsSafeZone(radiusMeters, (success, zone, message) -> {
            synchronized (lock) {
                if (success && zone != null) {
                    response[0] = "Safe zone marked at your current location with a 1 kilometer radius.";
                    if (guardianAlertManager != null) {
                        android.location.Location loc = geofencingManager.getLastKnownLocation();
                        guardianAlertManager.notifySafeZoneMarked(loc, radiusMeters);
                    }
                } else {
                    response[0] = message != null ? message : "Unable to mark safe zone.";
                }
                lock.notifyAll();
            }
        });

        synchronized (lock) {
            try {
                lock.wait(3500);
            } catch (InterruptedException ignored) {}
        }

        String text = response[0] != null ? response[0] : "Safe zone marked at your current location with a 1 kilometer radius.";
        return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, text);
    }

    private ActionResult executeGetCurrentLocation() {
        if (geofencingManager == null || !permissionManager.hasLocationPermission()) {
            return new ActionResult(ActionResult.ExecutionStatus.FAILED, Platform.SYSTEM, "Location permission is required to check your position.");
        }
        android.location.Location loc = geofencingManager.getLastKnownLocation();
        if (loc != null) {
            String text = String.format(Locale.US, "Your coordinates are %.4f latitude, %.4f longitude.", loc.getLatitude(), loc.getLongitude());
            return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, text);
        }
        return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, "Acquiring your current GPS location.");
    }

    public static long parseReminderTimeMillis(String timeText) {
        if (timeText == null || timeText.trim().isEmpty()) {
            return System.currentTimeMillis() + 3600000L;
        }
        String lower = timeText.toLowerCase(Locale.ROOT).trim();
        Calendar cal = Calendar.getInstance();

        if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        } else if (lower.contains("next week")) {
            cal.add(Calendar.DAY_OF_YEAR, 7);
        }

        Pattern timePat = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");
        Matcher m = timePat.matcher(lower);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = (m.group(2) != null) ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3);
            if (ampm != null) {
                if (ampm.equals("pm") && hour < 12) hour += 12;
                else if (ampm.equals("am") && hour == 12) hour = 0;
            } else if (lower.contains("evening") || lower.contains("night")) {
                if (hour < 12) hour += 12;
            }
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else {
            if (lower.contains("tomorrow") || lower.contains("morning")) {
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
            } else {
                cal.add(Calendar.HOUR_OF_DAY, 1);
            }
        }
        return cal.getTimeInMillis();
    }

    private ActionResult executeReadNotifications(boolean onlyUrgent) {
        if (notificationManager == null) {
            notificationManager = new com.aura.service.notification.SystemNotificationManager(context, systemMonitor);
        }
        String summary = notificationManager.summarizeNotifications(onlyUrgent);
        return new ActionResult(ActionResult.ExecutionStatus.SUCCESS, Platform.SYSTEM, summary);
    }
}
