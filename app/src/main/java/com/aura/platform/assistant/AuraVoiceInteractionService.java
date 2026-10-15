package com.aura.platform.assistant;

import android.content.Intent;
import android.service.voice.VoiceInteractionService;
import android.util.Log;
import com.aura.platform.AuraService;

public class AuraVoiceInteractionService extends VoiceInteractionService {
    private static final String TAG = "AuraVoiceService";

    @Override
    public void onReady() {
        super.onReady();
        try {
            Intent intent = new Intent(this, AuraService.class);
            startForegroundService(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch foreground service: " + e.getMessage(), e);
        }
    }
}
