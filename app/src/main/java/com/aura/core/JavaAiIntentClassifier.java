package com.aura.core;

import com.aura.models.Intent;
import com.aura.models.Platform;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaAiIntentClassifier {

    // Communication patterns
    private static final Pattern MSG_TEXT_SAYING = Pattern.compile("^(?:text|message|sms)\\s+([a-zA-Z0-9_\\s]+?)\\s+(?:saying|that|telling them|telling him|telling her|stating)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_SEND_TO_SAYING = Pattern.compile("^send\\s+(?:a\\s+)?(?:text|message|sms)\\s+to\\s+([a-zA-Z0-9_\\s]+?)\\s+(?:saying|that|telling them|telling him|telling her|stating)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_PUNCTUATION = Pattern.compile("^(?:send\\s+(?:a\\s+)?(?:text|message|sms)\\s+to|(?:text|message|sms))\\s+([a-zA-Z0-9_\\s]+?)\\s*[,:]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_TELL_SAYING = Pattern.compile("^tell\\s+(?!me\\s+)([a-zA-Z0-9_\\s]+?)\\s+(?:saying|that)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_TELL_DIRECT = Pattern.compile("^tell\\s+(?!me\\b)([a-zA-Z0-9_]+)\\s+((?:i|i'm|i'll|i am|i will|we|we're|can you|could you|please|where|call me|on my way|don't|do not)\\b.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_NATURAL_BODY = Pattern.compile("^(?:send\\s+(?:a\\s+)?(?:text|message|sms)\\s+to|(?:text|message|sms))\\s+([a-zA-Z0-9_]+(?:\\s+[a-zA-Z0-9_]+)?)\\s+((?:i|i'm|i'll|i am|i will|we|we're|we will|you|can you|could you|please|where|are you|how|what|when|hello|hey|hi|good|call me|meet me|on my way|don't|do not|happy|see you|love you|yes|no)\\b.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_SIMPLE_TEXT = Pattern.compile("^(?:text|message|sms)\\s+([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_SIMPLE_SEND_TO = Pattern.compile("^send\\s+(?:a\\s+)?(?:text|message|sms)\\s+to\\s+([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_INCOMPLETE = Pattern.compile("^(?:send\\s+(?:a\\s+)?(?:text|message|sms)|text someone|send message|compose message|new message|send a text|send a message)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CALL_PATTERN = Pattern.compile("^(?:call|dial|phone|make a call to|place a call to)\\s+([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALL_INCOMPLETE = Pattern.compile("^(?:call|make a call|place a call|dial|call someone|phone call)$", Pattern.CASE_INSENSITIVE);

    // Productivity & Time
    private static final Pattern ALARM_PATTERN = Pattern.compile("^(?:set\\s+(?:an?\\s+)?)?(?:alarm|wake\\s+me\\s+up)\\s+(?:for\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|in the morning|in the evening|in the afternoon|at night)?(?:\\s+(?:called|labeled|for)\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALARM_INCOMPLETE = Pattern.compile("^(?:set\\s+(?:an?\\s+)?)?alarm$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALARM_MODIFICATION = Pattern.compile("^(?:actually\\s+)?(?:make\\s+it|change\\s+(?:it|that)\\s+to)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIMER_PATTERN = Pattern.compile("^(?:set\\s+(?:a\\s+)?)?timer\\s+(?:for\\s+)?(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMER_INCOMPLETE = Pattern.compile("^(?:set\\s+(?:a\\s+)?)?timer$", Pattern.CASE_INSENSITIVE);

    private static final Pattern REMIND_PATTERN = Pattern.compile("^(?:remind\\s+me\\s+to|remind\\s+me\\s+about|set\\s+(?:a\\s+)?reminder\\s+(?:to|for|about)|add\\s+(?:a\\s+)?calendar\\s+event(?:\\s+for)?)\\s+(.+?)(?:\\s+(?:at|on|for)\\s+([0-9a-zA-Z\\s:]+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMIND_AT_PATTERN = Pattern.compile("^(?:remind\\s+me)\\s+(?:at|on|for)\\s+([0-9a-zA-Z\\s:]+?)\\s+to\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMIND_INCOMPLETE = Pattern.compile("^(?:remind\\s+me|set\\s+a\\s+reminder|add\\s+reminder|new\\s+reminder)$", Pattern.CASE_INSENSITIVE);

    // Memory & Object patterns
    private static final Pattern REMEMBER_IS_PATTERN = Pattern.compile("^(?:remember\\s+(?:that\\s+)?(?:my\\s+|the\\s+)?|(?:my|the)\\s+)(?!what|where|who|when|how|why)(.+?)\\s+(?:is|are)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMEMBER_LEFT_PUT = Pattern.compile("^(?:remember\\s+(?:that\\s+)?)?(?:i\\s+(?:left|put|kept|placed)\\s+(?:my\\s+|the\\s+)?)(.+?)\\s+(in|on|at|under|near|by|inside)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMEMBER_GENERAL = Pattern.compile("^remember\\s+(?:that\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECALL_WHERE_DID_I = Pattern.compile("^(?:where\\s+did\\s+i\\s+(?:put|leave|keep|place)\\s+(?:my\\s+|the\\s+)?)(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECALL_QUESTION = Pattern.compile("^(?:what\\s+is\\s+my|where\\s+is\\s+my|where\\s+are\\s+my|where's\\s+my|where\\s+is\\s+the|where\\s+are\\s+the|where's\\s+the|when\\s+is\\s+my)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECALL_GENERAL = Pattern.compile("^(?:recall|find|locate)\\s+(?:my\\s+|the\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGET_PATTERN = Pattern.compile("^(?:forget|delete)\\s+(?:that\\s+)?(?:memory\\s+)?(?:about\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NAVIGATE_PATTERN = Pattern.compile("^(?:navigate\\s+to|take\\s+me\\s+to|guide\\s+me\\s+to|directions\\s+to|how\\s+(?:do\\s+i\\s+get|to\\s+get)\\s+to)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_APP_PATTERN = Pattern.compile("^(?:open|launch)\\s+([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_PATTERN = Pattern.compile("^(?:search\\s+(?:the\\s+)?web\\s+for|search\\s+for|search|google)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIND_OBJ_PATTERN = Pattern.compile("^(?:scan\\s+for|look\\s+for\\s+(?:my\\s+)?|help\\s+me\\s+find\\s+(?:my\\s+)?|find\\s+(?:my\\s+)?)(.+?)(?:\\s+with\\s+camera)?$", Pattern.CASE_INSENSITIVE);

    // Math & Media
    private static final Pattern CALCULATE_PATTERN = Pattern.compile("^(?:what\\s+is|how\\s+much\\s+is|calculate)\\s+([0-9\\.\\s]+(?:\\s*(?:\\+|\\-|\\*|\\/|x|plus|minus|times|multiplied\\s+by|divided\\s+by)\\s*[0-9\\.\\s]+)+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_PATTERN = Pattern.compile("^(play|pause|stop|resume|next|previous|skip|rewind|forward)\\s+(?:the\\s+)?(?:music|song|songs|track|tracks|playback|audio)$", Pattern.CASE_INSENSITIVE);

    public Intent classify(String rawText) {
        return classify(rawText, null);
    }

    public Intent classify(String rawText, ConversationalContextEngine context) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new Intent.Unknown();
        }

        String text = rawText.trim();
        String cleanText = text.replaceAll("[.?!,;:]+$", "").trim();
        String lower = cleanText.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        // 1. Control & Privacy Actions (High Priority)
        if (lower.equals("cancel") || lower.equals("stop") || lower.equals("abort") || lower.equals("exit")) {
            return new Intent.Cancel();
        }
        if (lower.equals("stop listening") || lower.equals("stop listening to me") || lower.equals("go to sleep")) {
            return new Intent.StopListening();
        }
        if (lower.equals("repeat") || lower.equals("say that again") || lower.equals("what did you say")) {
            return new Intent.Repeat();
        }
        if (lower.equals("help") || lower.equals("what can you do")) {
            return new Intent.Help();
        }
        // Emergency contact queries & configuration
        boolean isSettingEmergencyContact =
            lower.startsWith("my emergency contact is ") || lower.startsWith("my emergency contact ") ||
            lower.startsWith("emergency contact is ") || lower.startsWith("emergency contact ") ||
            lower.startsWith("set my emergency contact to ") || lower.startsWith("set emergency contact to ") ||
            lower.startsWith("set my emergency contact ") || lower.startsWith("set emergency contact ") ||
            lower.startsWith("save emergency contact ") || lower.startsWith("save my emergency contact ") ||
            lower.startsWith("add emergency contact ") || lower.startsWith("add my emergency contact ") ||
            lower.startsWith("register emergency contact ") || lower.startsWith("register my emergency contact ") ||
            lower.startsWith("change emergency contact to ") || lower.startsWith("update emergency contact to ") ||
            lower.startsWith("my emergency number is ") || lower.startsWith("emergency number is ") ||
            lower.startsWith("set emergency number to ") || lower.startsWith("set my emergency number to ") ||
            lower.startsWith("my emergency contact number is ") || lower.startsWith("emergency contact number is ") ||
            lower.startsWith("my emergency phone is ") || lower.startsWith("emergency phone is ") ||
            lower.startsWith("set emergency phone to ") || lower.startsWith("set my emergency phone to ") ||
            lower.startsWith("my guardian is ") || lower.startsWith("guardian is ") ||
            lower.startsWith("set my guardian to ") || lower.startsWith("set guardian to ") ||
            lower.startsWith("set my guardian ") || lower.startsWith("set guardian ") ||
            lower.startsWith("add guardian ") || lower.startsWith("add my guardian ") ||
            lower.startsWith("save guardian ") || lower.startsWith("save my guardian ") ||
            lower.startsWith("register guardian ") || lower.startsWith("register my guardian ") ||
            lower.startsWith("my guardian number is ") || lower.startsWith("guardian number is ") ||
            lower.startsWith("set guardian number to ") || lower.startsWith("set my guardian number to ") ||
            lower.startsWith("my guardian phone is ") || lower.startsWith("guardian phone is ") ||
            lower.startsWith("set guardian phone to ") || lower.startsWith("set my guardian phone to ") ||
            lower.startsWith("my ice is ") || lower.startsWith("ice is ") ||
            lower.startsWith("my ice contact is ") || lower.startsWith("ice contact is ");

        if (isSettingEmergencyContact) {
            String val = text.replaceFirst("(?i)^(?:(?:set|save|add|register|change|update)\\s+)?(?:my\\s+)?(?:emergency|guardian|ice)(?:\\s+(?:contact|number|phone))?(?:\\s+number)?\\s*(?:is|to|as|:)?\\s*", "").trim();
            String key = lower.contains("guardian") ? "guardian" : "emergency contact";
            return new Intent.Remember(key, val);
        }

        if (lower.equals("who is my emergency contact") || lower.equals("who are my emergency contacts") ||
            lower.equals("check emergency contact") || lower.equals("what is my emergency contact") ||
            lower.equals("what is my emergency number") || lower.equals("check emergency number") ||
            lower.equals("my emergency contact") || lower.equals("emergency contact")) {
            return new Intent.Recall("emergency contact");
        }

        if (lower.equals("who is my guardian") || lower.equals("who is guardian") || lower.equals("what is my guardian") ||
            lower.equals("what is my guardian number") || lower.equals("check guardian number") || lower.equals("check guardian") ||
            lower.equals("my guardian") || lower.equals("guardian")) {
            return new Intent.Recall("guardian");
        }

        if (lower.equals("emergency") || lower.contains("help me") || lower.contains("i need help") ||
            lower.equals("send sos") || lower.equals("trigger sos") || lower.equals("sos") ||
            lower.equals("call 112") || lower.equals("call 911") || lower.equals("call emergency") ||
            lower.contains("call emergency") || lower.contains("call my emergency") ||
            lower.contains("call guardian") || lower.contains("call my guardian") ||
            lower.equals("call for help") || lower.contains("distress alert") ||
            lower.contains("alert emergency") || lower.contains("alert guardian") ||
            lower.contains("urgent help") || lower.equals("it s an emergency") || lower.equals("its an emergency") ||
            lower.equals("this is an emergency") || lower.equals("i have an emergency") ||
            lower.equals("emergency assistance") || lower.equals("emergency help") || lower.equals("emergency call") ||
            lower.startsWith("emergency ") || lower.endsWith(" emergency")) {
            return new Intent.Emergency();
        }
        if (lower.equals("status") || lower.equals("system status") || lower.equals("how are you")) {
            return new Intent.SystemStatus();
        }
        if (lower.equals("clear my conversation") || lower.equals("clear conversation") || lower.equals("reset conversation")) {
            return new Intent.ClearConversation();
        }
        if (lower.equals("clear my memory") || lower.equals("clear memory") || lower.equals("forget everything")) {
            return new Intent.ClearMemory();
        }

        // Voice profile & Speaker recognition
        if (lower.equals("register my voice") || lower.equals("recognize my voice") ||
            lower.equals("learn my voice") || lower.equals("enroll my voice") ||
            lower.equals("save my voice") || lower.equals("record my voice") ||
            lower.equals("set my voice profile") || lower.equals("lock to my voice") ||
            lower.equals("this is my voice") || lower.equals("remember my voice")) {
            return new Intent.RegisterVoice();
        }
        if (lower.equals("reset my voice") || lower.equals("clear my voice profile") ||
            lower.equals("re register my voice") || lower.equals("forget my voice") ||
            lower.equals("delete my voice profile") || lower.equals("reset voice profile") ||
            lower.equals("clear voice profile")) {
            return new Intent.ResetVoice();
        }
        if (lower.equals("is my voice registered") || lower.equals("check voice registration") ||
            lower.equals("voice status") || lower.equals("check voice status") ||
            lower.equals("is my voice recognized") || lower.equals("who can talk to aura") ||
            lower.equals("who can speak to aura") || lower.equals("voice profile status")) {
            return new Intent.GetVoiceStatus();
        }
        if (lower.equals("delete all stored aura data") || lower.equals("delete all data") || lower.equals("erase all data")) {
            return new Intent.DeleteAllData();
        }

        // 2. Active Clarification Resolution (If waiting for answers)
        if (context != null && context.getAwaitingClarificationField() != null) {
            String field = context.getAwaitingClarificationField();
            Intent pending = context.getPendingIncompleteIntent();
            context.clearClarification();

            if ("recipient".equalsIgnoreCase(field)) {
                String recipient = resolveTarget(cleanRecipient(cleanText), context);
                String body = null;
                if (pending instanceof Intent.SendMessage) {
                    body = ((Intent.SendMessage) pending).getBody();
                }
                return new Intent.SendMessage(recipient, body, Platform.SMS);
            } else if ("body".equalsIgnoreCase(field)) {
                String recipient = null;
                if (pending instanceof Intent.SendMessage) {
                    recipient = ((Intent.SendMessage) pending).getRecipient();
                }
                if (recipient == null || recipient.isEmpty()) {
                    recipient = context.getLastRecipientName();
                }
                return new Intent.SendMessage(recipient, text, Platform.SMS);
            } else if ("contact".equalsIgnoreCase(field)) {
                String contact = resolveTarget(cleanRecipient(cleanText), context);
                return new Intent.CallContact(contact);
            } else if ("time".equalsIgnoreCase(field)) {
                Matcher tm = ALARM_PATTERN.matcher("set an alarm for " + cleanText);
                if (tm.matches()) {
                    int hour = Integer.parseInt(tm.group(1));
                    int minute = tm.group(2) != null ? Integer.parseInt(tm.group(2)) : 0;
                    String ampm = tm.group(3);
                    if (ampm != null) {
                        if (ampm.equalsIgnoreCase("pm") && hour < 12) hour += 12;
                        else if (ampm.equalsIgnoreCase("am") && hour == 12) hour = 0;
                    }
                    return new Intent.SetAlarm(hour, minute, "Alarm");
                }
            } else if ("duration".equalsIgnoreCase(field)) {
                Matcher dm = TIMER_PATTERN.matcher("timer for " + cleanText);
                if (dm.matches()) {
                    int num = Integer.parseInt(dm.group(1));
                    String unit = dm.group(2).toLowerCase(Locale.ROOT);
                    int durationSecs = num;
                    if (unit.startsWith("min")) durationSecs = num * 60;
                    else if (unit.startsWith("hour") || unit.startsWith("hr")) durationSecs = num * 3600;
                    return new Intent.SetTimer(durationSecs, "Timer");
                }
            }
        }

        // 3. Conversational Context Modification (e.g. "Actually make it 6:30")
        Matcher modMatcher = ALARM_MODIFICATION.matcher(cleanText);
        if (modMatcher.matches()) {
            int hour = Integer.parseInt(modMatcher.group(1));
            int minute = modMatcher.group(2) != null ? Integer.parseInt(modMatcher.group(2)) : 0;
            String ampm = modMatcher.group(3);
            if (ampm != null) {
                if (ampm.equalsIgnoreCase("pm") && hour < 12) hour += 12;
                else if (ampm.equalsIgnoreCase("am") && hour == 12) hour = 0;
            }
            return new Intent.SetAlarm(hour, minute, "Alarm");
        }

        // 4. Device Controls (Flashlight, Bluetooth, Wi-Fi)
        if (lower.contains("flashlight") || lower.contains("torch")) {
            boolean turnOff = lower.contains("off") || lower.contains("disable") || lower.contains("switch off");
            return new Intent.ToggleFlashlight(!turnOff);
        }
        if (lower.contains("bluetooth")) {
            boolean turnOff = lower.contains("off") || lower.contains("disable");
            return new Intent.ToggleBluetooth(!turnOff);
        }
        if ((lower.contains("wifi") || lower.contains("wi fi")) && (lower.contains("turn on") || lower.contains("turn off") || lower.contains("enable") || lower.contains("disable"))) {
            boolean turnOff = lower.contains("off") || lower.contains("disable");
            return new Intent.ToggleWifi(!turnOff);
        }

        // 5. Messaging Patterns (With Pronoun Resolution)
        Matcher m = MSG_TEXT_SAYING.matcher(text);
        if (!m.matches()) m = MSG_TEXT_SAYING.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, m.group(2).trim(), Platform.SMS);
        }

        m = MSG_SEND_TO_SAYING.matcher(text);
        if (!m.matches()) m = MSG_SEND_TO_SAYING.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, m.group(2).trim(), Platform.SMS);
        }

        m = MSG_PUNCTUATION.matcher(text);
        if (!m.matches()) m = MSG_PUNCTUATION.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, m.group(2).trim(), Platform.SMS);
        }

        m = MSG_TELL_SAYING.matcher(text);
        if (!m.matches()) m = MSG_TELL_SAYING.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, m.group(2).trim(), Platform.SMS);
        }

        m = MSG_TELL_DIRECT.matcher(text);
        if (!m.matches()) m = MSG_TELL_DIRECT.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, m.group(2).trim(), Platform.SMS);
        }

        m = MSG_NATURAL_BODY.matcher(text);
        if (!m.matches()) m = MSG_NATURAL_BODY.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, m.group(2).trim(), Platform.SMS);
        }

        m = MSG_SIMPLE_SEND_TO.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, null, Platform.SMS);
        }

        m = MSG_SIMPLE_TEXT.matcher(cleanText);
        if (m.matches()) {
            String recipient = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.SendMessage(recipient, null, Platform.SMS);
        }

        if (MSG_INCOMPLETE.matcher(cleanText).matches()) {
            return new Intent.ClarificationNeeded(
                    "Who should I send the message to?",
                    "recipient",
                    new Intent.SendMessage(null, null, Platform.SMS)
            );
        }

        // 6. Phone Calls
        m = CALL_PATTERN.matcher(cleanText);
        if (m.matches()) {
            String contact = resolveTarget(cleanRecipient(m.group(1)), context);
            return new Intent.CallContact(contact);
        }

        if (CALL_INCOMPLETE.matcher(cleanText).matches()) {
            return new Intent.ClarificationNeeded(
                    "Who would you like to call?",
                    "contact",
                    new Intent.CallContact(null)
            );
        }

        // 7. Message Reading
        if (lower.equals("read my latest message") || lower.equals("read latest message") ||
            lower.equals("read my last message") || lower.equals("read my last text") ||
            lower.equals("read the last message") || lower.equals("read incoming message") ||
            lower.equals("read my messages") || lower.equals("read messages") ||
            lower.equals("read my text") || lower.equals("read text") ||
            lower.equals("read text message") || lower.equals("read my text messages") ||
            lower.equals("check messages") || lower.equals("check message") ||
            lower.equals("check text messages") || lower.equals("check my messages") ||
            lower.equals("check my texts") || lower.equals("read texts") || lower.equals("read my texts")) {
            return new Intent.ReadLatestMessage();
        }

        // 8. Timers & Alarms
        m = TIMER_PATTERN.matcher(cleanText);
        if (m.matches()) {
            int num = Integer.parseInt(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            int durationSecs = num;
            if (unit.startsWith("min")) durationSecs = num * 60;
            else if (unit.startsWith("hour") || unit.startsWith("hr")) durationSecs = num * 3600;
            HashMap<String, Object> p = new HashMap<>();
            p.put("duration", durationSecs);
            return new Intent.ExecuteTool("set_timer", p);
        }

        if (TIMER_INCOMPLETE.matcher(cleanText).matches()) {
            return new Intent.ClarificationNeeded(
                    "How long would you like the timer set for?",
                    "duration",
                    new Intent.SetTimer(-1, "Timer")
            );
        }

        // Alarms & Clock controls
        if (lower.equals("show alarms") || lower.equals("show my alarms") || lower.equals("list alarms") ||
            lower.equals("check alarms") || lower.equals("what alarms do i have") || lower.equals("what are my alarms")) {
            return new Intent.ShowAlarms();
        }
        if (lower.equals("open clock") || lower.equals("open the clock") || lower.equals("open clock app") ||
            lower.equals("clock application") || lower.equals("clock app") || lower.equals("show clock")) {
            return new Intent.OpenClock();
        }
        if (lower.equals("dismiss alarm") || lower.equals("stop alarm") || lower.equals("cancel alarm") ||
            lower.equals("turn off alarm") || lower.equals("shut off alarm") || lower.equals("silence alarm")) {
            return new Intent.DismissAlarm();
        }

        m = ALARM_PATTERN.matcher(cleanText);
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3);
            if (ampm != null) {
                String ampmLower = ampm.toLowerCase(Locale.ROOT);
                if ((ampmLower.contains("pm") || ampmLower.contains("evening") || ampmLower.contains("night") || ampmLower.contains("afternoon")) && hour < 12) {
                    hour += 12;
                } else if ((ampmLower.contains("am") || ampmLower.contains("morning")) && hour == 12) {
                    hour = 0;
                }
            }
            String label = (m.groupCount() >= 4 && m.group(4) != null) ? m.group(4).trim() : "Alarm";
            HashMap<String, Object> p = new HashMap<>();
            p.put("hour", hour);
            p.put("minute", minute);
            p.put("message", label);
            return new Intent.ExecuteTool("set_alarm", p);
        }

        if (ALARM_INCOMPLETE.matcher(cleanText).matches()) {
            return new Intent.ClarificationNeeded(
                    "What time should I set the alarm for?",
                    "time",
                    new Intent.SetAlarm(-1, -1, "Alarm")
            );
        }

        // 9. Calendar & Reminders controls
        if (lower.equals("open calendar") || lower.equals("open the calendar") || lower.equals("show calendar") ||
            lower.equals("check calendar") || lower.equals("open calendar app") || lower.equals("calendar application") ||
            lower.equals("calendar app") || lower.equals("what's on my calendar") || lower.equals("what is on my calendar") ||
            lower.equals("show my schedule") || lower.equals("show schedule")) {
            return new Intent.OpenCalendar();
        }
        if (lower.equals("what are my reminders") || lower.equals("show my reminders") || lower.equals("list my reminders") ||
            lower.equals("list reminders") || lower.equals("read my reminders") || lower.equals("check reminders") ||
            lower.equals("my reminders") || lower.equals("show reminders")) {
            return new Intent.ListReminders();
        }

        m = REMIND_AT_PATTERN.matcher(cleanText);
        if (m.matches()) {
            String time = m.group(1).trim();
            String title = m.group(2).trim();
            return new Intent.SetReminder(title, time);
        }

        m = REMIND_PATTERN.matcher(cleanText);
        if (m.matches()) {
            String title = m.group(1).trim();
            String time = m.group(2) != null ? m.group(2).trim() : "today";
            return new Intent.SetReminder(title, time);
        }
        if (REMIND_INCOMPLETE.matcher(cleanText).matches()) {
            return new Intent.ClarificationNeeded(
                    "What should I remind you about?",
                    "title",
                    new Intent.SetReminder(null, null)
            );
        }

        // 10. Memory Management
        if (lower.equals("what did i ask you to remember") || lower.equals("what do you remember") || lower.equals("list memories")) {
            return new Intent.ListMemories();
        }

        if (lower.startsWith("my guardian is ") || lower.startsWith("set my guardian to ") ||
            lower.startsWith("set guardian to ") || lower.startsWith("my guardian number is ") ||
            lower.startsWith("set guardian number to ") || lower.startsWith("my guardian phone is ") ||
            lower.startsWith("set guardian phone to ")) {
            String val = text.replaceFirst("(?i)^(?:my\\s+guardian(?:\\s+(?:number|phone))?\\s+is|set\\s+(?:my\\s+)?guardian(?:\\s+(?:number|phone))?\\s+to)\\s*", "").trim();
            return new Intent.Remember("emergency contact", val);
        }
        if (lower.equals("who is my guardian") || lower.equals("who is guardian") || lower.equals("what is my guardian") ||
            lower.equals("what is my guardian number") || lower.equals("check guardian number") || lower.equals("check guardian")) {
            return new Intent.Recall("emergency contact");
        }

        m = FORGET_PATTERN.matcher(cleanText);
        if (m.matches()) {
            return new Intent.Forget(m.group(1).trim());
        }

        // Indoor doorway locating query
        if (lower.equals("where is the doorway") || lower.equals("where is the door") ||
            lower.equals("where is door") || lower.equals("locate the door") ||
            lower.equals("locate doorway") || lower.equals("find the door") ||
            lower.equals("find doorway") || lower.equals("doorway locator") ||
            lower.equals("find the doorway")) {
            return new Intent.ExecuteTool("nav_locate_doorway", new HashMap<>());
        }

        m = RECALL_WHERE_DID_I.matcher(cleanText);
        if (m.matches()) {
            return new Intent.Recall(m.group(1).trim());
        }

        m = RECALL_QUESTION.matcher(cleanText);
        if (m.matches()) {
            return new Intent.Recall(m.group(1).trim());
        }

        m = RECALL_GENERAL.matcher(cleanText);
        if (m.matches()) {
            return new Intent.Recall(m.group(1).trim());
        }

        m = REMEMBER_LEFT_PUT.matcher(cleanText);
        if (m.matches()) {
            String item = m.group(1).trim();
            String prep = m.group(2).trim();
            String loc = m.group(3).trim();
            return new Intent.Remember(item, prep + " " + loc);
        }

        m = REMEMBER_IS_PATTERN.matcher(cleanText);
        if (m.matches()) {
            return new Intent.Remember(m.group(1).trim(), m.group(2).trim());
        }

        m = REMEMBER_GENERAL.matcher(cleanText);
        if (m.matches()) {
            String content = m.group(1).trim();
            String[] parts = content.split("(?i)\\s+is\\s+", 2);
            if (parts.length == 2) {
                return new Intent.Remember(parts[0].trim(), parts[1].trim());
            }
            return new Intent.Remember("note", content);
        }

        // 11. Vision & Obstacles
        if (lower.contains("read text with camera") || lower.contains("read text on camera") ||
            lower.equals("read this") || lower.equals("read document") || lower.equals("read sign") ||
            lower.equals("what does this say") || lower.equals("read what is written") ||
            lower.equals("scan text") || lower.contains("read this text")) {
            return new Intent.ExecuteTool("read_text_camera", new HashMap<>());
        }

        if (lower.equals("what color is this") || lower.equals("detect color") ||
            lower.equals("what color") || lower.equals("check color") ||
            lower.equals("identify color") || lower.contains("what color is in front of me")) {
            return new Intent.ExecuteTool("detect_color", new HashMap<>());
        }

        if (lower.equals("scan barcode") || lower.equals("scan product") ||
            lower.equals("read barcode") || lower.equals("identify product") ||
            lower.equals("read product label") || lower.equals("scan label")) {
            return new Intent.ExecuteTool("scan_product", new HashMap<>());
        }

        if (lower.equals("check lighting") || lower.equals("is it dark") ||
            lower.equals("is the light on") || lower.equals("how is the light") ||
            lower.equals("is it bright")) {
            return new Intent.ExecuteTool("check_lighting", new HashMap<>());
        }

        if (lower.contains("what do you see") || lower.contains("describe what is in front of me") ||
            lower.contains("describe what's in front of me") || lower.contains("describe scene") ||
            lower.contains("what is in front of me") || lower.contains("what's in front of me") ||
            lower.contains("look around") || lower.contains("describe surroundings") ||
            lower.contains("obstacle") || lower.contains("blocking my path") ||
            lower.contains("is my path clear") || lower.contains("check path") ||
            lower.contains("scan path") || lower.contains("read the document") ||
            lower.contains("read document") || lower.contains("what is this object") ||
            lower.contains("recognize currency") || lower.contains("what currency") ||
            lower.contains("read text in front of me")) {
            return new Intent.DescribeObstacles();
        }

        m = FIND_OBJ_PATTERN.matcher(cleanText);
        if (m.matches()) {
            HashMap<String, Object> p = new HashMap<>();
            p.put("objectName", m.group(1).trim());
            return new Intent.ExecuteTool("find_object", p);
        }

        // Safe Zone & Location Marking Keywords
        if (lower.equals("mark this location") || lower.equals("mark current location") ||
            lower.equals("mark my location") || lower.equals("mark safe zone") ||
            lower.equals("set safe zone here") || lower.equals("set safe zone") ||
            lower.equals("set a safe zone here") || lower.equals("mark location as safe zone") ||
            lower.equals("mark location") || lower.equals("set 1km safe zone") ||
            lower.equals("set 1 km safe zone")) {
            return new Intent.MarkSafeZone(1000.0f); // 1 km radius
        }
        if (lower.equals("where am i") || lower.equals("what is my location") || lower.equals("my location")) {
            return new Intent.GetCurrentLocation();
        }

        // 12. Spatial & House Navigation
        if (lower.equals("map my house") || lower.equals("start mapping") || lower.equals("map house")) {
            return new Intent.MapHouse();
        }
        if (lower.equals("stop navigation") || lower.equals("cancel navigation")) {
            return new Intent.StopNavigation();
        }

        if (lower.startsWith("i am in the ") || lower.startsWith("i'm in the ") ||
            lower.startsWith("i am in ") || lower.startsWith("i'm in ") ||
            lower.startsWith("set current room to ") || lower.startsWith("current room is ")) {
            String room = cleanText.replaceFirst("(?i)^(?:i(?:'m|\\s+am)\\s+in\\s+(?:the\\s+)?|set\\s+current\\s+room\\s+to\\s+(?:the\\s+)?|current\\s+room\\s+is\\s+(?:the\\s+)?)", "").trim();
            HashMap<String, Object> p = new HashMap<>();
            p.put("room", room);
            return new Intent.ExecuteTool("set_current_room", p);
        }

        if (lower.equals("where am i in the house") || lower.equals("what room am i in") ||
            lower.equals("which room am i in") || lower.equals("current room") || lower.equals("my current room")) {
            return new Intent.ExecuteTool("get_current_room", new HashMap<>());
        }

        if (lower.equals("list mapped rooms") || lower.equals("what rooms are mapped") ||
            lower.equals("show mapped rooms") || lower.equals("list rooms") ||
            lower.equals("saved rooms") || lower.equals("show rooms in house") ||
            lower.equals("rooms in house")) {
            return new Intent.ExecuteTool("list_mapped_rooms", new HashMap<>());
        }

        if (lower.startsWith("delete mapped room ") || lower.startsWith("delete room ") ||
            lower.startsWith("remove room ") || lower.startsWith("remove mapped room ")) {
            String room = cleanText.replaceFirst("(?i)^(?:delete|remove)\\s+(?:mapped\\s+)?room\\s+(?:the\\s+)?", "").trim();
            HashMap<String, Object> p = new HashMap<>();
            p.put("room", room);
            return new Intent.ExecuteTool("delete_mapped_room", p);
        }

        // Minute movement and indoor navigation commands
        if (lower.equals("next step") || lower.equals("what is my next step") ||
            lower.equals("what is the next step") || lower.equals("what's next") ||
            lower.equals("whats next") || lower.equals("next instruction") ||
            lower.equals("next navigation step") || lower.equals("next movement")) {
            return new Intent.ExecuteTool("nav_next_step", new HashMap<>());
        }

        if (lower.equals("how many steps left") || lower.equals("how many paces left") ||
            lower.equals("how many steps") || lower.equals("steps remaining") ||
            lower.equals("paces remaining") || lower.equals("distance left") ||
            lower.equals("distance remaining") || lower.equals("how far is it") ||
            lower.equals("how far") || lower.equals("am i close") ||
            lower.equals("how close am i") || lower.equals("navigation status")) {
            return new Intent.ExecuteTool("nav_steps_remaining", new HashMap<>());
        }

        if (lower.equals("check my heading") || lower.equals("check heading") ||
            lower.equals("check my direction") || lower.equals("check direction") ||
            lower.equals("am i aligned") || lower.equals("check alignment") ||
            lower.equals("am i facing the right way") || lower.equals("which way am i facing") ||
            lower.equals("alignment check")) {
            return new Intent.ExecuteTool("nav_check_alignment", new HashMap<>());
        }

        if (lower.equals("scan path ahead") || lower.equals("scan path") ||
            lower.equals("check path") || lower.equals("is the path clear") ||
            lower.equals("is path clear") || lower.equals("scan walking path") ||
            lower.contains("obstacle") || lower.contains("anything in front") ||
            lower.contains("what is in front") || lower.contains("what's in front") ||
            lower.equals("check obstacles") || lower.equals("scan for obstacles") ||
            lower.equals("detect obstacles") || lower.equals("is anything in my way")) {
            return new Intent.ExecuteTool("nav_scan_path", new HashMap<>());
        }

        Matcher stepMatcher = Pattern.compile("^(?:i\\s+(?:took|walked|stepped)|took)\\s+(\\d+)\\s+(?:steps|paces)$", Pattern.CASE_INSENSITIVE).matcher(cleanText);
        if (stepMatcher.matches()) {
            int paces = Integer.parseInt(stepMatcher.group(1));
            HashMap<String, Object> p = new HashMap<>();
            p.put("paces", paces);
            return new Intent.ExecuteTool("nav_record_steps", p);
        }
        if (lower.equals("i took a step") || lower.equals("took a step") || lower.equals("step taken") ||
            lower.equals("next pace") || lower.equals("step completed")) {
            HashMap<String, Object> p = new HashMap<>();
            p.put("paces", 1);
            return new Intent.ExecuteTool("nav_record_steps", p);
        }

        m = NAVIGATE_PATTERN.matcher(cleanText);
        if (m.matches()) {
            String dest = m.group(1).trim();
            boolean isIndoor = lower.contains("room") || lower.contains("kitchen") ||
                    lower.contains("bathroom") || lower.contains("bedroom") || lower.contains("hallway") ||
                    lower.contains("dining") || lower.contains("corridor") || lower.contains("balcony") ||
                    lower.contains("washroom") || lower.contains("toilet") || lower.contains("stairs") ||
                    lower.contains("staircase") || lower.contains("study") || lower.contains("patio") ||
                    lower.contains("inside") || lower.contains("indoor") || lower.contains("porch") ||
                    lower.contains("garage") || lower.contains("fridge") || lower.contains("refrigerator") ||
                    lower.contains("stove") || lower.contains("oven") || lower.contains("sink") ||
                    lower.contains("microwave") || lower.contains("couch") || lower.contains("sofa") ||
                    lower.contains("bed") || lower.contains("desk") || lower.contains("closet") ||
                    lower.contains("wardrobe") || lower.contains("door") || lower.contains("entrance") ||
                    lower.contains("lab") || lower.contains("library") || lower.contains("cafeteria") ||
                    lower.contains("reception") || lower.contains("elevator") || lower.contains("exit") ||
                    lower.contains("canteen");
            return new Intent.StartNavigation(dest, isIndoor);
        }

        // 13. System Diagnostics
        if (lower.contains("battery") || lower.contains("charge level")) {
            return new Intent.GetBatteryStatus();
        }
        if (lower.contains("network status") || lower.contains("internet connection")) {
            return new Intent.GetNetworkStatus();
        }
        if (lower.contains("storage") || lower.contains("device health") || lower.contains("space remaining")) {
            return new Intent.GetDeviceHealth();
        }

        // 14. Calculations / Math
        m = CALCULATE_PATTERN.matcher(text);
        if (m.matches()) {
            return new Intent.Calculate(m.group(1).trim());
        }

        // 15. Media & Volume Controls
        // Volume adjustments
        if (lower.equals("volume up") || lower.equals("turn up the volume") || lower.equals("turn up volume") ||
            lower.equals("increase volume") || lower.equals("increase the volume") || lower.equals("raise volume") ||
            lower.equals("raise the volume") || lower.equals("louder") || lower.equals("make it louder")) {
            return new Intent.MediaControl("volume_up");
        }
        if (lower.equals("volume down") || lower.equals("turn down the volume") || lower.equals("turn down volume") ||
            lower.equals("decrease volume") || lower.equals("decrease the volume") || lower.equals("lower volume") ||
            lower.equals("lower the volume") || lower.equals("quieter") || lower.equals("make it quieter") ||
            lower.equals("softer") || lower.equals("make it softer")) {
            return new Intent.MediaControl("volume_down");
        }
        if (lower.equals("mute") || lower.equals("mute music") || lower.equals("mute audio") ||
            lower.equals("mute sound") || lower.equals("mute volume") || lower.equals("silence music") ||
            lower.equals("silence audio")) {
            return new Intent.MediaControl("mute");
        }
        if (lower.equals("unmute") || lower.equals("unmute music") || lower.equals("unmute audio") ||
            lower.equals("unmute sound") || lower.equals("unmute volume") || lower.equals("restore sound")) {
            return new Intent.MediaControl("unmute");
        }
        if (lower.equals("max volume") || lower.equals("maximum volume") || lower.equals("full volume") ||
            lower.equals("set volume to maximum") || lower.equals("volume to max")) {
            return new Intent.MediaControl("max_volume");
        }
        if (lower.equals("volume level") || lower.equals("check volume") || lower.equals("what is the volume") ||
            lower.equals("current volume") || lower.equals("how loud is it")) {
            return new Intent.MediaControl("volume_status");
        }

        // Fast forward / Rewind / Music Player
        if (lower.equals("fast forward") || lower.equals("fast forward music") || lower.equals("fast forward track") ||
            lower.equals("forward song") || lower.equals("fast forward song")) {
            return new Intent.MediaControl("fast_forward");
        }
        if (lower.equals("rewind") || lower.equals("rewind music") || lower.equals("rewind track") ||
            lower.equals("rewind song")) {
            return new Intent.MediaControl("rewind");
        }
        if (lower.equals("open music") || lower.equals("open music player") || lower.equals("launch music") ||
            lower.equals("open radio") || lower.equals("play radio") || lower.equals("start radio")) {
            return new Intent.MediaControl("open_music");
        }

        // Media playback
        m = MEDIA_PATTERN.matcher(text);
        if (m.matches()) {
            return new Intent.MediaControl(m.group(1).toLowerCase(Locale.ROOT).trim());
        }
        if (lower.equals("play music") || lower.equals("pause music") || lower.equals("next song") ||
            lower.equals("previous song") || lower.equals("skip song") || lower.equals("stop music") ||
            lower.equals("play songs") || lower.equals("next track") || lower.equals("previous track") ||
            lower.equals("skip track") || lower.equals("resume music") || lower.equals("resume song") ||
            lower.equals("stop playback") || lower.equals("resume playback") || lower.equals("pause playback")) {
            String[] words = lower.split(" ");
            return new Intent.MediaControl(words[0]);
        }

        // 16. Notifications
        if (lower.contains("notification") || lower.contains("alerts") ||
            lower.equals("check alerts") || lower.equals("read alerts") ||
            lower.equals("read my alerts") || lower.equals("any alerts") ||
            lower.equals("urgent alerts") || lower.equals("system alerts") ||
            lower.equals("unread alerts")) {
            if (lower.contains("important") || lower.contains("urgent") || lower.contains("priority")) {
                return new Intent.ReadImportantNotifications();
            }
            return new Intent.ReadNotifications();
        }

        // 17. Time & Date
        if (lower.contains("time") && !lower.contains("timer")) {
            return new Intent.GetTime();
        }
        if (lower.contains("date") || lower.contains("today")) {
            return new Intent.GetDate();
        }

        // 18. Tools & Entertainment (Weather, Jokes, Riddles, Fun Facts, App, Web)
        if (lower.contains("weather") || lower.contains("temperature") || lower.contains("outside forecast")) {
            return new Intent.ExecuteTool("get_weather", new HashMap<>());
        }
        if (lower.contains("riddle") || lower.equals("tell me a riddle") || lower.equals("give me a riddle") ||
            lower.equals("ask me a riddle") || lower.equals("riddle me this")) {
            return new Intent.ExecuteTool("tell_riddle", new HashMap<>());
        }
        if (lower.contains("fun fact") || lower.contains("random fact") || lower.contains("interesting fact") ||
            lower.equals("tell me a fact") || lower.equals("give me a fact") || lower.equals("tell me trivia") ||
            lower.equals("trivia") || lower.equals("did you know")) {
            return new Intent.ExecuteTool("fun_fact", new HashMap<>());
        }
        if (lower.contains("joke") || lower.contains("tell me a joke") || lower.contains("laugh") ||
            lower.contains("make me laugh") || lower.equals("say something funny") || lower.equals("crack a joke")) {
            return new Intent.ExecuteTool("tell_joke", new HashMap<>());
        }

        m = OPEN_APP_PATTERN.matcher(text);
        if (m.matches()) {
            return new Intent.OpenApp(m.group(1).trim());
        }

        m = SEARCH_PATTERN.matcher(text);
        if (m.matches()) {
            return new Intent.SearchWeb(m.group(1).trim());
        }

        // 19. AI Configuration & Key Management
        if (lower.startsWith("set my ai key") || lower.startsWith("set ai key") ||
            lower.startsWith("set gemini key") || lower.startsWith("set my gemini key") ||
            lower.startsWith("set ai api key") || lower.startsWith("set gemini api key") ||
            lower.startsWith("set my ai api key") || lower.startsWith("set my gemini api key")) {
            String key = cleanText.replaceFirst("(?i)^set\\s+(?:my\\s+)?(?:ai|gemini)\\s+(?:api\\s+)?key\\s+(?:to\\s+)?", "").trim();
            return new Intent.SetAiKey(key);
        }

        if (lower.equals("ai status") || lower.equals("check ai status") ||
            lower.equals("what is the ai status") || lower.equals("what is ai status") ||
            lower.equals("check ai") || lower.equals("is ai active") ||
            lower.equals("ai model status") || lower.equals("chatbot status")) {
            return new Intent.GetAiStatus();
        }

        // 20. Clear Conversation History
        if (lower.equals("clear conversation") || lower.equals("clear my conversation") ||
            lower.equals("reset conversation") || lower.equals("clear conversation history") ||
            lower.equals("clear our conversation history") || lower.equals("clear chat") ||
            lower.equals("reset chat")) {
            return new Intent.ClearConversation();
        }

        // Multi-sentence or emotional preamble handling (e.g. "I'm really angry. Send a message to Rahul.")
        String[] sentences = rawText.split("[.!?;]+");
        if (sentences.length > 1) {
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase(rawText.trim())) {
                    Intent clauseIntent = classify(trimmed, context);
                    if (!(clauseIntent instanceof Intent.Unknown) && !(clauseIntent instanceof Intent.AskAi)) {
                        return clauseIntent;
                    }
                }
            }
        }

        if (cleanText.isEmpty()) {
            return new Intent.Unknown();
        }

        // Universal Conversational AI:
        // Any question, explanation, reasoning task, or open-ended dialogue
        // is dynamically routed to the integrated general-purpose AI language model!
        return new Intent.AskAi(cleanText);
    }

    private String resolveTarget(String candidate, ConversationalContextEngine context) {
        if (candidate == null) return "";
        if (context != null) {
            String resolved = context.resolvePronoun(candidate);
            if (resolved != null) return resolved;
        }
        return candidate;
    }

    private String cleanRecipient(String recipient) {
        if (recipient == null) return "";
        return recipient.trim()
                .replaceAll("^(?i)(?:to\\s+|my\\s+|please\\s+)", "")
                .replaceAll("(?i)\\s+(?:on\\s+)?(?:mobile|phone|cell|number)$", "")
                .replaceAll("(?i)\\s+please$", "")
                .replaceAll("(?i)('s)$", "")
                .replaceAll("[.?!,;:]+$", "")
                .trim();
    }
}
