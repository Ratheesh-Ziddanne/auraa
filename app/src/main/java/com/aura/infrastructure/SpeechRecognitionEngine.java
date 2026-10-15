package com.aura.infrastructure;

public interface SpeechRecognitionEngine {
    void listen(SpeechListener listener);
    void stop();
    default void cancel() {
        stop();
    }

    interface SpeechListener {
        void onSuccess(String transcript, Float confidence);
        void onFailure(String error);
    }
}
