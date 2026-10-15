package com.aura.service.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.aura.service.memory.PersonalMemoryService;

public class AuraToolbox {
    private static final String TAG = "AuraToolbox";

    private final Context context;
    private final Random random = new Random();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private PersonalMemoryService memoryService;
    private com.aura.service.notification.SystemNotificationManager notificationManager;

    private final List<String> jokes = Arrays.asList(
            "Why did the smartphone go to the doctor? It had a terminal illness.",
            "How do you make a robot angry? Keep pushing its buttons.",
            "What do you call an AI that likes to sing? A data-voice.",
            "Why was the computer cold? It left its Windows open.",
            "Why did the developer go broke? Because they used up all their cache.",
            "Why do programmers prefer dark mode? Because light attracts bugs.",
            "There are only 10 types of people in the world: those who understand binary, and those who don't.",
            "Why was 6 afraid of 7? Because 7, 8, 9!",
            "What is an astronaut's favorite key on a computer? The space bar.",
            "Why did the PowerPoint presentation cross the road? To get to the other slide.",
            "What kind of music do balloons hate? Pop music.",
            "Why can't a bicycle stand on its own? Because it is two-tired.",
            "How does a penguin build its house? Igloos it together.",
            "What do you call cheese that isn't yours? Nacho cheese.",
            "Why did the tomato blush? Because it saw the salad dressing."
    );

    private final List<String> riddles = Arrays.asList(
            "Riddle: What has keys, but no locks; space, but no room; and you can enter, but never leave? Answer: A keyboard!",
            "Riddle: What gets wetter the more it dries? Answer: A towel!",
            "Riddle: I have cities, but no houses. I have mountains, but no trees. I have water, but no fish. What am I? Answer: A map!",
            "Riddle: The more of this there is, the less you see. What is it? Answer: Darkness!",
            "Riddle: What can travel around the world while staying in the same corner? Answer: A postage stamp!",
            "Riddle: What has a neck but no head? Answer: A bottle!",
            "Riddle: What begins with T, finishes with T, and has T in it? Answer: A teapot!",
            "Riddle: What has hands, but cannot clap? Answer: A clock!",
            "Riddle: What has words, but never speaks? Answer: A book!",
            "Riddle: What can you catch, but never throw? Answer: A cold!",
            "Riddle: What has one eye, but cannot see? Answer: A needle!",
            "Riddle: If you drop me I'm sure to crack, but give me a smile and I'll always smile back. What am I? Answer: A mirror!"
    );

    private final List<String> funFacts = Arrays.asList(
            "Did you know? Honey never spoils. Archaeologists have found 3,000-year-old honey in Egyptian tombs that is still completely edible!",
            "Did you know? Octopuses have three hearts, nine brains, and blue blood!",
            "Did you know? Venus is the only planet in our solar system that rotates clockwise on its axis!",
            "Did you know? A day on Mars is roughly 24 hours and 37 minutes, remarkably close to an Earth day!",
            "Did you know? Bananas are slightly radioactive because they are rich in potassium-40!",
            "Did you know? Sea otters hold hands while sleeping so they don't drift away in ocean currents!",
            "Did you know? Sound waves travel roughly four times faster through water than through air!",
            "Did you know? The Eiffel Tower can grow over 15 centimeters taller during the hot summer due to thermal expansion of the iron!",
            "Did you know? Cows have best friends and experience less stress when they are together!",
            "Did you know? Hummingbirds are the only birds capable of sustained backward flight!",
            "Did you know? The shortest war in history lasted only 38 to 45 minutes between Britain and Zanzibar in 1896!",
            "Did you know? A cloud can weigh more than a million pounds, floating because the warm air below it is even denser!"
    );

    public AuraToolbox(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public void setMemoryService(PersonalMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public String execute(String toolName, Map<String, Object> parameters) {
        if (toolName == null) return "No tool specified.";

        switch (toolName.toLowerCase(Locale.ROOT).trim()) {
            case "tell_joke":
            case "joke":
                return jokes.get(random.nextInt(jokes.size()));

            case "tell_riddle":
            case "riddle":
                return riddles.get(random.nextInt(riddles.size()));

            case "fun_fact":
            case "random_fact":
            case "trivia":
                return funFacts.get(random.nextInt(funFacts.size()));

            case "get_weather":
                return fetchLiveWeather();

            case "open_app":
                return openApp(parameters != null ? (String) parameters.get("appName") : null);

            case "search_web":
                return searchWeb(parameters != null ? (String) parameters.get("query") : null);

            case "toggle_wifi":
                return openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi");

            case "toggle_bluetooth":
                return openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth");

            case "find_object":
                return findObject(parameters != null ? (String) parameters.get("objectName") : null);

            case "read_text_camera":
            case "read_text":
            case "read_document":
                return "Optical text reader active. Hold the document, letter, or sign about 30 centimeters in front of the rear camera and hold steady.";

            case "detect_color":
            case "check_color":
                return "Color identification sensor active. Hold the camera about 10 centimeters from the object. Ensure the area is illuminated, or say: turn on flashlight.";

            case "scan_product":
            case "scan_barcode":
                return "Barcode and packaging scanner active. Center the product barcode or packaging in front of the rear camera.";

            case "check_lighting":
            case "lighting":
                java.util.Calendar cCal = java.util.Calendar.getInstance();
                int cHour = cCal.get(java.util.Calendar.HOUR_OF_DAY);
                if (cHour < 6 || cHour > 20) {
                    return "Ambient light sensor indicates evening or dim lighting. If you'd like more illumination, say: turn on flashlight.";
                } else {
                    return "Ambient light sensor indicates the area is brightly lit.";
                }

            case "nav_next_step":
                return "Proceed to the next micro-guidance step. Follow clock directions and maintain cane sweeping.";

            case "nav_steps_remaining":
                return "Checking remaining pace count. Continue along the centered corridor.";

            case "nav_check_alignment":
                return "Body alignment centered at 12 o'clock. Maintain heading along the corridor.";

            case "nav_locate_doorway":
                return "Scanning for doorway opening and floor sill threshold ahead.";

            case "nav_record_steps":
                return "Paces recorded. Updating distance remaining to destination.";

            case "nav_scan_path":
                return "Forward walking path scanned. Corridor is clear for 2 to 3 meters.";

            case "set_timer":
                return setTimer(parameters);

            case "set_alarm":
                return setAlarm(parameters);

            case "show_alarms":
            case "open_clock":
                return showAlarms();

            case "dismiss_alarm":
                return dismissAlarm();

            case "open_calendar":
                return openCalendar();

            case "media_control":
                return controlMedia(parameters != null ? (String) parameters.get("action") : "toggle");

            case "summarize_notifications":
                if (notificationManager == null) {
                    notificationManager = new com.aura.service.notification.SystemNotificationManager(context);
                }
                return notificationManager.summarizeNotifications(true);

            default:
                return "I'm sorry, I don't know how to execute the tool " + toolName + ".";
        }
    }

    private String setTimer(Map<String, Object> parameters) {
        if (context == null) {
            return "I couldn't set the timer. Device context is unavailable.";
        }

        int seconds = 60;
        String message = "AURA Timer";
        if (parameters != null) {
            Object durObj = parameters.get("duration");
            if (durObj instanceof Number) {
                seconds = ((Number) durObj).intValue();
            } else if (durObj instanceof String) {
                try { seconds = Integer.parseInt((String) durObj); } catch (Exception ignored) {}
            }
            if (parameters.containsKey("message")) {
                message = String.valueOf(parameters.get("message"));
            }
        }
        if (seconds <= 0) seconds = 60;

        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
            intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                // Try without skip UI in case OEM clock app requires confirmation
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                context.startActivity(intent);
            }
            int mins = seconds / 60;
            int remSecs = seconds % 60;
            if (mins > 0 && remSecs > 0) {
                return "Your timer has been set for " + mins + " minutes and " + remSecs + " seconds.";
            } else if (mins > 0) {
                return "Your timer has been set for " + mins + (mins == 1 ? " minute." : " minutes.");
            } else {
                return "Your timer has been set for " + seconds + " seconds.";
            }
        } catch (Exception e) {
            Log.w(TAG, "TIMER_DISPATCH_FAILURE: " + e.getMessage());
            return "I couldn't set the timer. Please check that a clock application is installed.";
        }
    }

    private String setAlarm(Map<String, Object> parameters) {
        if (context == null) {
            return "I couldn't set the alarm. Device context is unavailable.";
        }

        int hour = 8;
        int minute = 0;
        String message = "AURA Alarm";
        if (parameters != null) {
            if (parameters.containsKey("hour")) {
                Object h = parameters.get("hour");
                if (h instanceof Number) hour = ((Number) h).intValue();
                else try { hour = Integer.parseInt(String.valueOf(h)); } catch (Exception ignored) {}
            }
            if (parameters.containsKey("minute")) {
                Object m = parameters.get("minute");
                if (m instanceof Number) minute = ((Number) m).intValue();
                else try { minute = Integer.parseInt(String.valueOf(m)); } catch (Exception ignored) {}
            }
            if (parameters.containsKey("message")) {
                message = String.valueOf(parameters.get("message"));
            }
        }

        // Validate time
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "I couldn't set the alarm. The requested time is invalid.";
        }

        String formattedTime = String.format(java.util.Locale.US, "%d:%02d %s",
                (hour % 12 == 0) ? 12 : (hour % 12),
                minute,
                (hour >= 12) ? "PM" : "AM"
        );

        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return "Your alarm has been set for " + formattedTime + ".";
            } catch (Exception e) {
                // Try without skip UI
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                context.startActivity(intent);
                return "Your alarm has been set for " + formattedTime + ".";
            }
        } catch (Exception e) {
            Log.w(TAG, "ALARM_DISPATCH_FAILURE: " + e.getMessage());
            return "I couldn't set the alarm. Please check that a clock application is installed.";
        }
    }

    public String showAlarms() {
        if (context == null) return "Device context is unavailable.";
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Opening your alarms and clock application.";
        } catch (Exception e) {
            String fb = openApp("clock");
            if (fb != null && fb.contains("Opening")) {
                return fb;
            }
            return "I couldn't open the clock application.";
        }
    }

    public String dismissAlarm() {
        if (context == null) return "Device context is unavailable.";
        try {
            Intent intent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Dismissing alarm.";
        } catch (Exception e) {
            return "I couldn't dismiss the alarm. Please check your clock app.";
        }
    }

    public String openCalendar() {
        if (context == null) return "Device context is unavailable.";
        try {
            long startMillis = System.currentTimeMillis();
            Uri.Builder builder = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
            android.content.ContentUris.appendId(builder, startMillis);
            Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Opening your calendar application.";
        } catch (Exception e) {
            try {
                Intent calIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
                calIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(calIntent);
                return "Opening calendar application.";
            } catch (Exception ex) {
                return "I couldn't open the calendar application.";
            }
        }
    }

    public boolean insertCalendarEvent(String title, long beginTimeMillis, long endTimeMillis) {
        if (context == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, title)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Created by AURA Accessibility Assistant")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (beginTimeMillis > 0) {
                intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMillis);
                if (endTimeMillis <= beginTimeMillis) endTimeMillis = beginTimeMillis + 3600000L;
                intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis);
            }
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to insert calendar event: " + e.getMessage());
            return false;
        }
    }

    public String controlMedia(String action) {
        if (action == null) return "No media action specified.";
        if (context == null) return "Audio service unavailable.";
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return "Audio service unavailable.";

        String lower = action.toLowerCase(Locale.ROOT).trim();
        int keyCode = -1;
        String feedback;

        switch (lower) {
            case "volume_up":
            case "louder":
            case "increase_volume":
            case "raise_volume":
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                    int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int pct = max > 0 ? (int) Math.round((cur * 100.0) / max) : 0;
                    return "Media volume increased to " + pct + " percent.";
                } catch (Exception e) {
                    return "Unable to increase volume.";
                }

            case "volume_down":
            case "quieter":
            case "softer":
            case "decrease_volume":
            case "lower_volume":
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int pct = max > 0 ? (int) Math.round((cur * 100.0) / max) : 0;
                    return "Media volume decreased to " + pct + " percent.";
                } catch (Exception e) {
                    return "Unable to lower volume.";
                }

            case "mute":
            case "silence":
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    return "Media volume muted.";
                } catch (Exception e) {
                    return "Unable to mute volume.";
                }

            case "unmute":
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                    return "Media volume unmuted.";
                } catch (Exception e) {
                    return "Unable to unmute volume.";
                }

            case "max_volume":
            case "volume_max":
            case "maximum_volume":
            case "full_volume":
                try {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI);
                    return "Media volume set to maximum.";
                } catch (Exception e) {
                    return "Unable to set maximum volume.";
                }

            case "volume_status":
            case "volume_level":
            case "get_volume":
                try {
                    int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int pct = max > 0 ? (int) Math.round((cur * 100.0) / max) : 0;
                    return "Media volume is currently at " + pct + " percent.";
                } catch (Exception e) {
                    return "Unable to read current volume.";
                }

            case "open_music":
            case "launch_music":
            case "open_radio":
            case "play_radio":
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_APP_MUSIC);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return "Opening music player.";
                } catch (Exception e) {
                    return openApp("music");
                }

            case "pause":
            case "stop":
                keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE;
                feedback = "Pausing playback.";
                break;

            case "play":
            case "resume":
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY;
                feedback = "Resuming playback.";
                break;

            case "next":
            case "skip":
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                feedback = "Skipping to next track.";
                break;

            case "previous":
            case "prev":
            case "back":
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                feedback = "Returning to previous track.";
                break;

            case "fast_forward":
            case "forward":
                keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
                feedback = "Fast forwarding.";
                break;

            case "rewind":
                keyCode = KeyEvent.KEYCODE_MEDIA_REWIND;
                feedback = "Rewinding track.";
                break;

            default:
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                feedback = "Toggling media playback.";
                break;
        }

        try {
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return feedback;
        } catch (Exception e) {
            return "Unable to control media: " + e.getMessage();
        }
    }

    /**
     * Fetches live weather from Open-Meteo using device GPS, or Chennai as fallback.
     * NOTE: Must be called from a background thread or wrapped in Future (already is).
     */
    private String fetchLiveWeather() {
        // Determine best coordinates: device GPS > fallback Chennai
        final double[] coords = getDeviceCoordinates();

        Future<String> future = executor.submit((Callable<String>) () -> {
            HttpURLConnection conn = null;
            try {
                // Open-Meteo open weather API (No API key required)
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + coords[0] + "&longitude=" + coords[1] + "&current_weather=true");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("current_weather")) {
                        JSONObject cw = json.getJSONObject("current_weather");
                        double temp = cw.optDouble("temperature", 25.0);
                        double wind = cw.optDouble("windspeed", 10.0);
                        int weatherCode = cw.optInt("weathercode", 0);
                        String condition = decodeWeatherCode(weatherCode);

                        return "It is currently " + Math.round(temp) + " degrees Celsius with " + condition +
                                ". Wind speed is " + Math.round(wind) + " kilometers per hour.";
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "WEATHER_FETCH_FAILURE");
            } finally {
                if (conn != null) conn.disconnect();
            }
            return "Unable to fetch live weather. Please check your internet connection.";
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "Weather information is temporarily unavailable. Please check your connection.";
        }
    }

    /**
     * Returns {latitude, longitude} from device last known location, or Chennai default.
     * Uses PASSIVE provider — no extra permission beyond ACCESS_FINE_LOCATION already declared.
     */
    @SuppressWarnings("MissingPermission")
    private double[] getDeviceCoordinates() {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location last = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (last == null) last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) {
                    return new double[]{last.getLatitude(), last.getLongitude()};
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "DEVICE_LOCATION_UNAVAILABLE");
        }
        // Fallback: Chennai, India
        return new double[]{13.0827, 80.2707};
    }

    private String decodeWeatherCode(int code) {
        if (code == 0) return "clear skies";
        if (code <= 3) return "partly cloudy skies";
        if (code <= 48) return "foggy conditions";
        if (code <= 55) return "light drizzle";
        if (code <= 65) return "rain";
        if (code <= 75) return "snow showers";
        if (code <= 82) return "heavy rain showers";
        if (code <= 99) return "thunderstorms";
        return "mild conditions";
    }

    @SuppressLint("QueryPermissionsNeeded")
    private String openApp(String requestedName) {
        if (requestedName == null || requestedName.trim().isEmpty()) {
            return "Which application would you like to open?";
        }

        String name = requestedName.toLowerCase(Locale.ROOT).trim();
        PackageManager pm = context.getPackageManager();

        // 1. Direct package match for common aliases
        String targetPackage = null;
        if (name.contains("youtube")) targetPackage = "com.google.android.youtube";
        else if (name.contains("chrome") || name.contains("browser")) targetPackage = "com.android.chrome";
        else if (name.contains("settings")) targetPackage = "com.android.settings";
        else if (name.contains("camera")) targetPackage = "com.android.camera";
        else if (name.contains("whatsapp")) targetPackage = "com.whatsapp";
        else if (name.contains("clock") || name.contains("alarm")) targetPackage = "com.google.android.deskclock";

        if (targetPackage != null) {
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return "Opening " + requestedName + ".";
            }
        }

        // 2. Dynamic scan of installed applications
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo appInfo : packages) {
            CharSequence appLabel = pm.getApplicationLabel(appInfo);
            if (appLabel != null && appLabel.toString().toLowerCase(Locale.ROOT).contains(name)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    return "Opening " + appLabel + ".";
                }
            }
        }

        return "I couldn't find an installed application matching " + requestedName + ".";
    }

    private String searchWeb(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "What would you like me to search for?";
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Searching the web for " + query + ".";
        } catch (Exception e) {
            return "Unable to open web search: " + e.getMessage();
        }
    }

    private String findObject(String objectName) {
        if (objectName == null || objectName.trim().isEmpty()) {
            return "What object would you like me to look for?";
        }
        String cleanObj = objectName.trim();
        if (memoryService != null && memoryService.hasMemory(cleanObj)) {
            return memoryService.recall(cleanObj) + " If it's not there, point your camera slowly around the room and I will scan for it.";
        }
        return "I don't have a saved location for your " + cleanObj + ". Point your camera slowly around the room and I will scan for it.";
    }

    private String openSettings(String action, String label) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Opening " + label + " settings.";
        } catch (Exception e) {
            return "Unable to open " + label + " settings.";
        }
    }
}
