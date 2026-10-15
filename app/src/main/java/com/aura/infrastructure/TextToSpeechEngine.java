package com.aura.infrastructure;

public interface TextToSpeechEngine extends AutoCloseable {
    void speak(String text, float rate);
    default void speak(String text, float rate, Runnable onDone) {
        speak(text, rate);
    }
    default void speak(String text, float rate, float pitch, Runnable onDone) {
        speak(text, rate, onDone);
    }
    void stop();
    boolean isSpeaking();
    @Override
    void close();
}
