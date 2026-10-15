package com.aura.models;

public abstract class Intent {
    public abstract SafetyLevel getSafetyLevel();

    // 1. Messaging & Contacts
    public static class SendMessage extends Intent {
        private final String recipient;
        private final String body;
        private final Platform platform;

        public SendMessage(String recipient, String body, Platform platform) {
            this.recipient = recipient;
            this.body = body;
            this.platform = platform;
        }

        public String getRecipient() { return recipient; }
        public String getBody() { return body; }
        public Platform getPlatform() { return platform; }

        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.HIGH; }
    }

    public static class CallContact extends Intent {
        private final String contactName;
        public CallContact(String contactName) { this.contactName = contactName; }
        public String getContactName() { return contactName; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.HIGH; }
    }

    public static class SearchMessages extends Intent {
        private final String query;
        public SearchMessages(String query) { this.query = query; }
        public String getQuery() { return query; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class SummarizeConversation extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class GetLastMessage extends Intent {
        private final String contactName;
        public GetLastMessage(String contactName) { this.contactName = contactName; }
        public String getContactName() { return contactName; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    // 2. Notifications
    public static class ReadNotifications extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ReadImportantNotifications extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 3. Navigation
    public static class StartNavigation extends Intent {
        private final String destination;
        private final Boolean isIndoor;
        public StartNavigation(String destination, Boolean isIndoor) {
            this.destination = destination;
            this.isIndoor = isIndoor;
        }
        public String getDestination() { return destination; }
        public Boolean isIndoor() { return isIndoor; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.HIGH; }
    }

    public static class StopNavigation extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 4. Memory & Mapping
    public static class Remember extends Intent {
        private final String key;
        private final String value;
        public Remember(String key, String value) { this.key = key; this.value = value; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Recall extends Intent {
        private final String query;
        public Recall(String query) { this.query = query; }
        public String getQuery() { return query; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Forget extends Intent {
        private final String key;
        public Forget(String key) { this.key = key; }
        public String getKey() { return key; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ListMemories extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class MapHouse extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 5. Digital Intelligence
    public static class GetTime extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class GetDate extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class OpenApp extends Intent {
        private final String appName;
        public OpenApp(String appName) { this.appName = appName; }
        public String getAppName() { return appName; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class SearchWeb extends Intent {
        private final String query;
        public SearchWeb(String query) { this.query = query; }
        public String getQuery() { return query; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class MediaControl extends Intent {
        private final String action;
        public MediaControl(String action) { this.action = action; }
        public String getAction() { return action; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Calculate extends Intent {
        private final String expression;
        public Calculate(String expression) { this.expression = expression; }
        public String getExpression() { return expression; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class TakeScreenshot extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class OpenFolder extends Intent {
        private final String folderName;
        public OpenFolder(String folderName) { this.folderName = folderName; }
        public String getFolderName() { return folderName; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 6. Vision & Obstacles
    public static class DescribeObstacles extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 7. System Monitoring & Safety
    public static class GetBatteryStatus extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class GetNetworkStatus extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class GetDeviceHealth extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class SetSafeZone extends Intent {
        private final String name;
        private final int radius;
        public SetSafeZone(String name, int radius) {
            this.name = name;
            this.radius = radius;
        }
        public String getName() { return name; }
        public int getRadius() { return radius; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    // 8. System & Control
    public static class Emergency extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.CRITICAL; }
    }

    public static class SystemStatus extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ExecuteTool extends Intent {
        private final String toolName;
        private final java.util.Map<String, Object> parameters;
        public ExecuteTool(String toolName, java.util.Map<String, Object> parameters) {
            this.toolName = toolName;
            this.parameters = parameters;
        }
        public String getToolName() { return toolName; }
        public java.util.Map<String, Object> getParameters() { return parameters; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Cancel extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Repeat extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Help extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class Unknown extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 9. Time & Productivity Commands
    public static class SetAlarm extends Intent {
        private final int hour;
        private final int minute;
        private final String label;
        public SetAlarm(int hour, int minute, String label) {
            this.hour = hour;
            this.minute = minute;
            this.label = label != null ? label : "Alarm";
        }
        public int getHour() { return hour; }
        public int getMinute() { return minute; }
        public String getLabel() { return label; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class SetTimer extends Intent {
        private final int seconds;
        private final String label;
        public SetTimer(int seconds, String label) {
            this.seconds = seconds;
            this.label = label != null ? label : "Timer";
        }
        public int getSeconds() { return seconds; }
        public String getLabel() { return label; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class SetReminder extends Intent {
        private final String title;
        private final String timeText;
        public SetReminder(String title, String timeText) {
            this.title = title;
            this.timeText = timeText;
        }
        public String getTitle() { return title; }
        public String getTimeText() { return timeText; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class ShowAlarms extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class DismissAlarm extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class OpenClock extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class OpenCalendar extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ListReminders extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 10. Device Control Commands
    public static class ToggleBluetooth extends Intent {
        private final boolean enable;
        public ToggleBluetooth(boolean enable) { this.enable = enable; }
        public boolean isEnable() { return enable; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class ToggleFlashlight extends Intent {
        private final boolean enable;
        public ToggleFlashlight(boolean enable) { this.enable = enable; }
        public boolean isEnable() { return enable; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ToggleWifi extends Intent {
        private final boolean enable;
        public ToggleWifi(boolean enable) { this.enable = enable; }
        public boolean isEnable() { return enable; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class ReadLatestMessage extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    // 11. Privacy & Lifecycle
    public static class StopListening extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ClearConversation extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class ClearMemory extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.HIGH; }
    }

    public static class DeleteAllData extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.HIGH; }
    }

    // 12. Clarification Engine
    public static class ClarificationNeeded extends Intent {
        private final String question;
        private final String missingField;
        private final Intent originalIntent;

        public ClarificationNeeded(String question, String missingField, Intent originalIntent) {
            this.question = question;
            this.missingField = missingField;
            this.originalIntent = originalIntent;
        }

        public String getQuestion() { return question; }
        public String getMissingField() { return missingField; }
        public Intent getOriginalIntent() { return originalIntent; }

        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 13. Location & Safe Zone Marking
    public static class MarkSafeZone extends Intent {
        private final float radiusMeters;
        public MarkSafeZone(float radiusMeters) { this.radiusMeters = radiusMeters; }
        public MarkSafeZone() { this(1000.0f); } // Default 1 km radius
        public float getRadiusMeters() { return radiusMeters; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class GetCurrentLocation extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 14. Universal Conversational AI Intelligence
    public static class AskAi extends Intent {
        private final String question;
        public AskAi(String question) { this.question = question; }
        public String getQuestion() { return question; }
        public String getQuery() { return question; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    public static class SetAiKey extends Intent {
        private final String apiKey;
        public SetAiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiKey() { return apiKey; }
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.HIGH; }
    }

    public static class GetAiStatus extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }

    // 15. Voice Profile & Speaker Recognition
    public static class RegisterVoice extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class ResetVoice extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.MEDIUM; }
    }

    public static class GetVoiceStatus extends Intent {
        @Override
        public SafetyLevel getSafetyLevel() { return SafetyLevel.LOW; }
    }
}
