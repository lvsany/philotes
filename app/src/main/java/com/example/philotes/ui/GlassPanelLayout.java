package com.example.philotes.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * Lightweight glass panel.
 *
 * NOTE:
 * Applying RenderEffect blur directly to a View will also blur its children,
 * which makes texts and controls unreadable. For now we keep a translucent
 * glass tint only, and avoid blurring child content.
 */
public class GlassPanelLayout extends FrameLayout {

    public GlassPanelLayout(Context context) {
        super(context);
        init();
    }

    public GlassPanelLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlassPanelLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        // 20% white tint for glassmorphism feel.
        setBackgroundColor(Color.argb(51, 255, 255, 255));
    }
}
