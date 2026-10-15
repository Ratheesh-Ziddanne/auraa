package com.aura.experience;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class HapticFeedbackManager {
    private final Vibrator vibrator;

    public HapticFeedbackManager(Context context) {
        if (context == null) {
            this.vibrator = null;
            return;
        }
        Context appCtx = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) appCtx.getSystemService(Context.VIBRATOR_SERVICE);
            this.vibrator = vib;
        }
    }

    public void success() {
        vibrate(50, 180);
    }

    public void error() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            long[] timings = new long[]{0, 100, 100, 100};
            int[] amplitudes = new int[]{0, 255, 0, 255};
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } catch (Exception ignored) {}
    }

    public void prompt() {
        vibrate(40, 120);
    }

    public void confirmationRequired() {
        vibrate(80, 220);
    }

    private void vibrate(long durationMs, int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            int safeAmp = Math.max(1, Math.min(255, amplitude));
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, safeAmp));
        } catch (Exception ignored) {}
    }
}
