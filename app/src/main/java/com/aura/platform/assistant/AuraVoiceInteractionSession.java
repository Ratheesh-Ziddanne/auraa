package com.aura.platform.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import com.aura.platform.AuraService;

public class AuraVoiceInteractionSession extends VoiceInteractionSession {
    private static final String TAG = "AuraVoiceSession";

    public AuraVoiceInteractionSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        hide(); // Eyes-free: hide visual overlay immediately
        try {
            Intent intent = new Intent(getContext(), AuraService.class);
            intent.setAction(AuraService.ACTION_TRIGGER_WAKE);
            getContext().startForegroundService(intent);
            AuraService.triggerManualWake();
        } catch (Exception e) {
            Log.w(TAG, "Failed to start AuraService on voice summon: " + e.getMessage(), e);
        }
    }
}
