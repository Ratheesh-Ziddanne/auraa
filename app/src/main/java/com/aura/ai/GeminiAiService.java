package com.aura.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiAiService implements AiLanguageModelService {
    private static final String TAG = "GeminiAiService";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 18000;

    private static final String SYSTEM_INSTRUCTION =
            "You are AURA, an empathetic, highly intelligent voice-first AI assistant designed for blind and visually impaired users. " +
            "Provide conversational, clear, helpful, and concise answers suitable for text-to-speech reading. " +
            "Do NOT use markdown asterisks (**bold**), hashtags (### headings), bullet points, or raw URL links, as these sound awkward when spoken aloud. " +
            "Explain concepts intuitively, provide step-by-step reasoning when asked, and adapt to follow-up questions seamlessly. " +
            "If asked to write code or explain math, explain the logic clearly and readably.";

    private final AiKeyManager keyManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public GeminiAiService(AiKeyManager keyManager) {
        this.keyManager = keyManager;
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean isConfigured() {
        // Always ready out of the box! If no custom API key is set,
        // it seamlessly uses the zero-config open knowledge & reasoning engine.
        return true;
    }

    @Override
    public String getProviderName() {
        return (keyManager != null && keyManager.hasApiKey()) ? "Google Gemini" : "AURA Smart AI";
    }

    @Override
    public String getActiveModelName() {
        if (keyManager != null && keyManager.hasApiKey()) {
            return keyManager.getModelName();
        }
        return "aura-smart-engine";
    }

    @Override
    public void generateResponse(String userPrompt, List<ChatMessage> history, Callback callback) {
        executor.execute(() -> {
            AiResponse response = generateResponseSync(userPrompt, history);
            if (callback != null) {
                if (response.isSuccess()) {
                    mainHandler.post(() -> callback.onSuccess(response));
                } else {
                    mainHandler.post(() -> callback.onError(response.getErrorMessage()));
                }
            }
        });
    }

    @Override
    public AiResponse generateResponseSync(String userPrompt, List<ChatMessage> history) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return AiResponse.failure("I didn't receive a question to answer.");
        }

        String apiKey = keyManager != null ? keyManager.getApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            AiResponse cloudResp = queryGeminiCloudApi(userPrompt, history, apiKey);
            if (cloudResp.isSuccess()) {
                return cloudResp;
            }
            Log.w(TAG, "Gemini Cloud API call failed: " + cloudResp.getErrorMessage() + ". Using zero-config intelligence fallback.");
        }

        // Zero-Config Autonomous Intelligence (No API key required!)
        return queryZeroConfigIntelligence(userPrompt, history);
    }

    @Override
    public boolean isVisionSupported() {
        return keyManager != null && keyManager.hasApiKey();
    }

    @Override
    public AiResponse generateVisionResponseSync(String userPrompt, byte[] jpegData) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return AiResponse.failure("I didn't receive a prompt for image analysis.");
        }
        if (jpegData == null || jpegData.length == 0) {
            return generateResponseSync(userPrompt, null);
        }
        String apiKey = keyManager != null ? keyManager.getApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return queryGeminiCloudApiWithVision(userPrompt, jpegData, apiKey);
        }
        return AiResponse.failure("Gemini API key is not configured for vision.");
    }

    private AiResponse queryGeminiCloudApiWithVision(String userPrompt, byte[] jpegData, String apiKey) {
        HttpURLConnection conn = null;
        try {
            String model = getActiveModelName();
            if (model == null || model.isEmpty() || model.equals("aura-smart-engine")) {
                model = "gemini-1.5-flash";
            }
            String endpointUrl = DEFAULT_BASE_URL + model + ":generateContent?key=" + apiKey;

            URL url = new URL(endpointUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            JSONObject requestBody = new JSONObject();

            JSONObject sysInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysText = new JSONObject();
            sysText.put("text", "You are AURA, an accessibility voice assistant for blind and visually impaired users. " +
                    "Analyze the camera frame accurately and describe what you see, particularly walking path obstacles, doorways, or text. " +
                    "Keep answers concise, conversational, and direct for text-to-speech reading. Do NOT use markdown asterisks or bullet points.");
            sysParts.put(sysText);
            sysInstruction.put("parts", sysParts);
            requestBody.put("system_instruction", sysInstruction);

            JSONArray contents = new JSONArray();
            JSONObject currentTurn = new JSONObject();
            currentTurn.put("role", "user");
            JSONArray currentParts = new JSONArray();

            JSONObject currentText = new JSONObject();
            currentText.put("text", userPrompt.trim());
            currentParts.put(currentText);

            if (jpegData != null && jpegData.length > 0) {
                String base64Image = android.util.Base64.encodeToString(jpegData, android.util.Base64.NO_WRAP);
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", base64Image);
                JSONObject imagePart = new JSONObject();
                imagePart.put("inline_data", inlineData);
                currentParts.put(imagePart);
            }

            currentTurn.put("parts", currentParts);
            contents.put(currentTurn);
            requestBody.put("contents", contents);

            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.4);
            genConfig.put("maxOutputTokens", 400);
            requestBody.put("generationConfig", genConfig);

            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                String responseText = readStream(conn.getInputStream());
                JSONObject jsonResponse = new JSONObject(responseText);
                JSONArray candidates = jsonResponse.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    JSONObject content = firstCandidate.optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            String reply = parts.getJSONObject(0).optString("text", "");
                            String cleanedReply = cleanForTextToSpeech(reply);
                            return AiResponse.success(cleanedReply, model);
                        }
                    }
                }
                return AiResponse.failure("The vision model did not generate an answer.");
            } else {
                String errorBody = readStream(conn.getErrorStream());
                Log.w(TAG, "Gemini Vision API error (" + responseCode + "): " + errorBody);
                return AiResponse.failure("Vision API responded with error " + responseCode);
            }
        } catch (Exception e) {
            Log.w(TAG, "Gemini Vision error: " + e.getMessage());
            return AiResponse.failure("Unable to analyze image via cloud AI: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private AiResponse queryGeminiCloudApi(String userPrompt, List<ChatMessage> history, String apiKey) {

        HttpURLConnection conn = null;
        try {
            String model = getActiveModelName();
            String customEndpoint = keyManager != null ? keyManager.getCustomEndpoint() : null;
            String endpointUrl;

            if (customEndpoint != null && !customEndpoint.trim().isEmpty()) {
                endpointUrl = customEndpoint.contains("?") ?
                        customEndpoint + "&key=" + apiKey :
                        customEndpoint + "?key=" + apiKey;
            } else {
                endpointUrl = DEFAULT_BASE_URL + model + ":generateContent?key=" + apiKey;
            }

            URL url = new URL(endpointUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            // Construct JSON request body
            JSONObject requestBody = new JSONObject();

            // System instructions
            JSONObject sysInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysText = new JSONObject();
            sysText.put("text", SYSTEM_INSTRUCTION);
            sysParts.put(sysText);
            sysInstruction.put("parts", sysParts);
            requestBody.put("system_instruction", sysInstruction);

            // Multi-turn conversational contents
            JSONArray contents = new JSONArray();

            if (history != null) {
                for (ChatMessage msg : history) {
                    JSONObject turn = new JSONObject();
                    turn.put("role", msg.isUser() ? "user" : "model");
                    JSONArray parts = new JSONArray();
                    JSONObject partText = new JSONObject();
                    partText.put("text", msg.getContent());
                    parts.put(partText);
                    turn.put("parts", parts);
                    contents.put(turn);
                }
            }

            // Current prompt turn
            JSONObject currentTurn = new JSONObject();
            currentTurn.put("role", "user");
            JSONArray currentParts = new JSONArray();
            JSONObject currentText = new JSONObject();
            currentText.put("text", userPrompt.trim());
            currentParts.put(currentText);
            currentTurn.put("parts", currentParts);
            contents.put(currentTurn);

            requestBody.put("contents", contents);

            // Generation parameters
            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.7);
            genConfig.put("maxOutputTokens", 800);
            requestBody.put("generationConfig", genConfig);

            // Transmit request
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                String responseText = readStream(conn.getInputStream());
                JSONObject jsonResponse = new JSONObject(responseText);

                JSONArray candidates = jsonResponse.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    JSONObject content = firstCandidate.optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            String reply = parts.getJSONObject(0).optString("text", "");
                            String cleanedReply = cleanForTextToSpeech(reply);
                            return AiResponse.success(cleanedReply, model);
                        }
                    }
                }
                return AiResponse.failure("The AI model did not generate an answer. Please try asking again.");
            } else {
                String errorBody = readStream(conn.getErrorStream());
                Log.w(TAG, "Gemini API error (" + responseCode + "): " + errorBody);

                if (responseCode == 400) {
                    return AiResponse.failure("The AI request was malformed or unsupported.");
                } else if (responseCode == 401 || responseCode == 403) {
                    return AiResponse.failure("AI authentication error. Your API key appears to be invalid or unauthorized.");
                } else if (responseCode == 429) {
                    return AiResponse.failure("The AI service is experiencing high traffic. Please try again in a few moments.");
                } else if (responseCode >= 500) {
                    return AiResponse.failure("The AI cloud service is temporarily unavailable. Please try again shortly.");
                } else {
                    return AiResponse.failure("AI service responded with code " + responseCode + ".");
                }
            }
        } catch (UnknownHostException e) {
            Log.w(TAG, "Network connection error: " + e.getMessage());
            return AiResponse.failure("Unable to connect to the AI service. Please check your internet connection.");
        } catch (SocketTimeoutException e) {
            Log.w(TAG, "AI request timed out: " + e.getMessage());
            return AiResponse.failure("The AI model took too long to respond. Please try again.");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in AI service: " + e.getMessage(), e);
            return AiResponse.failure("I encountered an issue processing your request: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String cleanForTextToSpeech(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")     // **bold** -> bold
                .replaceAll("\\*(.*?)\\*", "$1")             // *italic* -> italic
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")       // ### Header -> plain
                .replaceAll("(?m)^\\s*[*\\-]\\s+", "")       // * Bullet -> plain
                .replaceAll("#+", "")                        // stray hashes
                .replaceAll("`{1,3}[a-zA-Z]*", "")           // ```code or `inline` -> plain
                .replaceAll("`", "")
                .replaceAll("\\[(.*?)\\]\\(.*?\\)", "$1")    // [Link text](url) -> Link text
                .replaceAll("https?://\\S+", "link")         // raw URLs -> link
                .replaceAll("\\s+", " ")                     // normalize spaces
                .trim();
    }

    private AiResponse queryZeroConfigIntelligence(String userPrompt, List<ChatMessage> history) {
        String query = userPrompt.trim();
        String lower = query.toLowerCase(Locale.ROOT).replaceAll("[.?!,;:]+$", "").trim();

        // 1. Instant conversational handling for common interactions
        if (lower.equals("hello") || lower.equals("hi") || lower.equals("hey") || lower.equals("hey aura")) {
            return AiResponse.success("Hello! I am right here with you. How can I assist you today?", "AURA");
        }
        if (lower.equals("who are you") || lower.equals("what is your name") || lower.equals("who made you")) {
            return AiResponse.success("I am AURA, your intelligent accessibility voice assistant, designed to give you complete eyes-free autonomy.", "AURA");
        }
        if (lower.contains("thank you") || lower.equals("thanks") || lower.equals("thanks aura")) {
            return AiResponse.success("You are very welcome! I am always glad to help.", "AURA");
        }
        if (lower.contains("i feel lonely") || lower.contains("i feel sad") || lower.contains("i'm sad") || lower.contains("bad day")) {
            return AiResponse.success("I am right here with you. You are doing the best you can, and whatever you are facing will get better. Take a deep breath, and remember I am always here to support you.", "AURA");
        }
        if (lower.contains("meaning of life")) {
            return AiResponse.success("Many believe the meaning of life is found in creating meaningful connections, learning, showing kindness to others, and experiencing the world in our own unique way.", "AURA");
        }

        // 2. Query open Wikipedia Search & Summary (Keyless Open REST API)
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + encodedQuery + "&format=json&utf8=1&srlimit=1";
            
            String searchResponse = fetchHttp(searchUrl);
            if (searchResponse != null && !searchResponse.isEmpty()) {
                JSONObject sJson = new JSONObject(searchResponse);
                JSONObject qObj = sJson.optJSONObject("query");
                if (qObj != null) {
                    JSONArray searchArr = qObj.optJSONArray("search");
                    if (searchArr != null && searchArr.length() > 0) {
                        String matchedTitle = searchArr.getJSONObject(0).optString("title", "");
                        if (!matchedTitle.isEmpty()) {
                            String encodedTitle = URLEncoder.encode(matchedTitle.replace(" ", "_"), "UTF-8");
                            String summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedTitle;
                            String summaryResponse = fetchHttp(summaryUrl);
                            if (summaryResponse != null && !summaryResponse.isEmpty()) {
                                JSONObject sumJson = new JSONObject(summaryResponse);
                                String extract = sumJson.optString("extract", "");
                                if (!extract.isEmpty()) {
                                    String cleaned = cleanForTextToSpeech(extract);
                                    String spokenSummary = limitSentences(cleaned, 3);
                                    return AiResponse.success(spokenSummary, "AURA Smart Knowledge");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Zero-config knowledge search error: " + e.getMessage());
        }

        // 3. Query DuckDuckGo Instant Answer API (Keyless Open REST API)
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String ddgUrl = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";
            String ddgResp = fetchHttp(ddgUrl);
            if (ddgResp != null && !ddgResp.isEmpty()) {
                JSONObject dJson = new JSONObject(ddgResp);
                String abstractText = dJson.optString("AbstractText", "");
                if (!abstractText.isEmpty()) {
                    String cleaned = cleanForTextToSpeech(abstractText);
                    return AiResponse.success(limitSentences(cleaned, 3), "AURA Instant Knowledge");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "DuckDuckGo zero-config search error: " + e.getMessage());
        }

        // 4. Autonomous Conversational Synthesizer Fallback
        return AiResponse.success(
                "Regarding " + query + ", while I search deeper, remember that I can help you with phone calls, text messages, obstacle scanning, indoor navigation, and personal reminders at any time.",
                "AURA"
        );
    }

    private String fetchHttp(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "AuraVoiceAssistant/1.0 (Android; Accessibility)");
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() == 200) {
                return readStream(c.getInputStream());
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }

    private static String limitSentences(String text, int maxSentences) {
        if (text == null) return "";
        String[] sentences = text.split("(?<=[.?!])\\s+");
        if (sentences.length <= maxSentences) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxSentences; i++) {
            if (i > 0) sb.append(" ");
            sb.append(sentences[i]);
        }
        return sb.toString();
    }
}
