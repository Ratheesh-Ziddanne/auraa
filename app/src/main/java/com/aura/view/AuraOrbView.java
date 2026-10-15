package com.aura.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public final class AuraOrbView extends View {
    public enum State {
        STANDBY, LISTENING, PROCESSING, SPEAKING, CONFIRMING, EMERGENCY
    }

    private final Paint paintCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRing1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRing2 = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ValueAnimator pulseAnimator;
    private ValueAnimator waveAnimator;

    private float pulseFactor = 1.0f;
    private float waveProgress = 0.0f;
    private float touchScale = 1.0f;

    private int primaryColor = Color.parseColor("#00E5FF");
    private int secondaryColor = Color.parseColor("#00B8D4");

    private RadialGradient cachedGradient = null;
    private float lastRadius = 0;
    private float lastCx = 0;
    private float lastCy = 0;

    private State currentState = State.STANDBY;

    public AuraOrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AuraOrbView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setContentDescription("AURA voice assistant visualizer orb. Tap to speak.");
        setFocusable(true);

        // Core breathing animator
        pulseAnimator = ValueAnimator.ofFloat(0.95f, 1.12f);
        pulseAnimator.setDuration(1600);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            pulseFactor = (float) animation.getAnimatedValue();
            invalidate();
        });

        // Ethereal ripple wave animator
        waveAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        waveAnimator.setDuration(2400);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.addUpdateListener(animation -> {
            waveProgress = (float) animation.getAnimatedValue();
            invalidate();
        });

        paintRing1.setStyle(Paint.Style.STROKE);
        paintRing1.setStrokeWidth(3f);

        paintRing2.setStyle(Paint.Style.STROKE);
        paintRing2.setStrokeWidth(2f);
    }

    public void setStateColors(int primary, int secondary) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
        cachedGradient = null;

        // Auto-detect visual tempo based on hue
        if (primary == Color.parseColor("#00E5FF")) {
            setVisualState(State.LISTENING);
        } else if (primary == Color.parseColor("#7C4DFF")) {
            setVisualState(State.PROCESSING);
        } else if (primary == Color.parseColor("#00E676")) {
            setVisualState(State.SPEAKING);
        } else if (primary == Color.parseColor("#FF5252") || primary == Color.parseColor("#FF3D00")) {
            setVisualState(State.EMERGENCY);
        } else if (primary == Color.parseColor("#FFD600")) {
            setVisualState(State.CONFIRMING);
        } else {
            setVisualState(State.STANDBY);
        }

        invalidate();
    }

    public void setVisualState(State state) {
        this.currentState = state;
        if (pulseAnimator != null) {
            switch (state) {
                case LISTENING:
                    pulseAnimator.setDuration(1000);
                    break;
                case PROCESSING:
                    pulseAnimator.setDuration(700);
                    break;
                case SPEAKING:
                    pulseAnimator.setDuration(1200);
                    break;
                case EMERGENCY:
                    pulseAnimator.setDuration(500);
                    break;
                case STANDBY:
                default:
                    pulseAnimator.setDuration(1800);
                    break;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchScale = 0.92f;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                touchScale = 1.0f;
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                touchScale = 1.0f;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pulseAnimator != null && !pulseAnimator.isRunning()) {
            pulseAnimator.start();
        }
        if (waveAnimator != null && !waveAnimator.isRunning()) {
            waveAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        if (waveAnimator != null) waveAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float baseRadius = Math.min(cx, cy) * 0.58f;
        float currentRadius = baseRadius * pulseFactor * touchScale;

        // 1. Draw outer glowing atmospheric halo
        float haloRadius = currentRadius * 1.35f;
        paintHalo.setColor(primaryColor);
        paintHalo.setAlpha((int) (40 * (2.0f - pulseFactor)));
        canvas.drawCircle(cx, cy, haloRadius, paintHalo);

        // 2. Draw dynamic expanding harmonic ripple rings
        float r1 = currentRadius + (haloRadius - currentRadius) * waveProgress;
        int alpha1 = (int) (160 * (1.0f - waveProgress));
        paintRing1.setColor(primaryColor);
        paintRing1.setAlpha(Math.max(0, Math.min(255, alpha1)));
        canvas.drawCircle(cx, cy, r1, paintRing1);

        float wave2Progress = (waveProgress + 0.5f) % 1.0f;
        float r2 = currentRadius + (haloRadius - currentRadius) * wave2Progress;
        int alpha2 = (int) (120 * (1.0f - wave2Progress));
        paintRing2.setColor(secondaryColor);
        paintRing2.setAlpha(Math.max(0, Math.min(255, alpha2)));
        canvas.drawCircle(cx, cy, r2, paintRing2);

        // 3. Draw luminous chromatic core sphere
        if (cachedGradient == null || Math.abs(currentRadius - lastRadius) > 2.0f || cx != lastCx || cy != lastCy) {
            lastRadius = currentRadius;
            lastCx = cx;
            lastCy = cy;
            cachedGradient = new RadialGradient(
                    cx, cy - (currentRadius * 0.15f), Math.max(1.0f, currentRadius * 1.05f),
                    new int[]{primaryColor, secondaryColor, Color.argb(0, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor))},
                    new float[]{0f, 0.72f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            paintCore.setShader(cachedGradient);
        }

        canvas.drawCircle(cx, cy, currentRadius, paintCore);
    }
}
