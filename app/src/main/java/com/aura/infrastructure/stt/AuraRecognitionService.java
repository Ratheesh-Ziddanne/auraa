package com.aura.infrastructure.stt;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import com.aura.infrastructure.JavaSpeechRecognitionEngine;
import com.aura.infrastructure.SpeechRecognitionEngine;
import java.util.ArrayList;

public class AuraRecognitionService extends RecognitionService {
    private JavaSpeechRecognitionEngine engine;

    @Override
    public void onCreate() {
        super.onCreate();
        engine = new JavaSpeechRecognitionEngine(this);
    }

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        if (engine == null) return;
        engine.listen(new SpeechRecognitionEngine.SpeechListener() {
            @Override
            public void onSuccess(String transcript, Float confidence) {
                try {
                    Bundle bundle = new Bundle();
                    ArrayList<String> results = new ArrayList<>();
                    results.add(transcript);
                    bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, results);
                    float[] conf = new float[]{confidence != null ? confidence : 1.0f};
                    bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, conf);
                    listener.results(bundle);
                } catch (Exception ignored) {}
            }

            @Override
            public void onFailure(String error) {
                try {
                    listener.error(SpeechRecognizer.ERROR_NO_MATCH);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    protected void onCancel(Callback listener) {
        if (engine != null) engine.cancel();
    }

    @Override
    protected void onStopListening(Callback listener) {
        if (engine != null) engine.stop();
    }

    @Override
    public void onDestroy() {
        if (engine != null) engine.stop();
        super.onDestroy();
    }
}
