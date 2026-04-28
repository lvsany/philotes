package com.example.philotes.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Animated orb that visualizes AI lifecycle states.
 */
public class AiStateOrbView extends androidx.appcompat.widget.AppCompatImageView {

    public enum State {
        IDLE,
        CAPTURING,
        THINKING,
        READY,
        ERROR
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private State state = State.IDLE;
    private ValueAnimator phaseAnimator;
    private ValueAnimator pulseAnimator;

    private float phase = 0f;
    private float pulse = 1f;

    public AiStateOrbView(Context context) {
        super(context);
        init();
    }

    public AiStateOrbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AiStateOrbView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStrokeWidth(dp(4f));

        glowPaint.setStyle(Paint.Style.FILL);
        corePaint.setStyle(Paint.Style.FILL);

        startAnimators();
    }

    public void setState(State newState) {
        if (newState == null || newState == state) {
            return;
        }
        state = newState;
        configureAnimators();
        invalidate();
    }

    private void startAnimators() {
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f);
        phaseAnimator.setDuration(1800);
        phaseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        phaseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        phaseAnimator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });

        pulseAnimator = ValueAnimator.ofFloat(0.94f, 1.08f);
        pulseAnimator.setDuration(2800);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            pulse = (float) animation.getAnimatedValue();
            invalidate();
        });

        configureAnimators();
        phaseAnimator.start();
        pulseAnimator.start();
    }

    private void configureAnimators() {
        if (phaseAnimator == null || pulseAnimator == null) {
            return;
        }

        switch (state) {
            case CAPTURING:
                phaseAnimator.setDuration(1100);
                break;
            case THINKING:
                phaseAnimator.setDuration(1500);
                break;
            case READY:
                phaseAnimator.setDuration(1200);
                break;
            case ERROR:
                phaseAnimator.setDuration(1300);
                break;
            case IDLE:
            default:
                phaseAnimator.setDuration(2600);
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        float base = Math.min(w, h) / 2f;
        float scaled = base * pulse;
        float ringRadius = scaled - dp(4f);
        float coreRadius = scaled - dp(12f);

        int accent = accentColor();
        int soft = softColor();

        glowPaint.setColor(withAlpha(soft, state == State.THINKING ? 120 : 80));
        canvas.drawCircle(cx, cy, ringRadius + dp(4f), glowPaint);

        ringPaint.setColor(accent);
        arcRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);
        float sweep = state == State.IDLE ? 110f : 200f;
        canvas.drawArc(arcRect, phase * 360f, sweep, false, ringPaint);

        corePaint.setColor(withAlpha(accent, 90));
        canvas.drawCircle(cx, cy, coreRadius, corePaint);

        corePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, coreRadius * 0.52f, corePaint);
    }

    private int accentColor() {
        switch (state) {
            case CAPTURING:
                return Color.parseColor("#4F8CFF");
            case THINKING:
                return Color.parseColor("#2D6BFF");
            case READY:
                return Color.parseColor("#32C987");
            case ERROR:
                return Color.parseColor("#E16363");
            case IDLE:
            default:
                return Color.parseColor("#6A8EC7");
        }
    }

    private int softColor() {
        switch (state) {
            case READY:
                return Color.parseColor("#B8F3D7");
            case ERROR:
                return Color.parseColor("#FFD5D5");
            case THINKING:
                return Color.parseColor("#D7E7FF");
            case CAPTURING:
                return Color.parseColor("#CCE0FF");
            case IDLE:
            default:
                return Color.parseColor("#E4EEFF");
        }
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (phaseAnimator != null) {
            phaseAnimator.cancel();
        }
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }
}
