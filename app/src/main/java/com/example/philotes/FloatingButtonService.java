package com.example.philotes;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FloatingButtonService extends AccessibilityService {
    private static final String CHANNEL_ID = "FloatingButtonServiceChannel";
    private static final String TAG = "FloatingButtonService";

    private WindowManager windowManager;
    private View floatingView;
    private View iconView;
    private View cardView;
    private TextView tvCardContent;
    private WindowManager.LayoutParams params;

    private boolean isFloatingViewAdded = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotificationChannel();
        // 启动前台服务以保持存活
        startForeground(1, createNotification());

        initFloatingView();

        Toast.makeText(this, "悬浮截屏服务已启动", Toast.LENGTH_SHORT).show();
    }

    private void initFloatingView() {
        if (isFloatingViewAdded) return;

        Context themedContext = new android.view.ContextThemeWrapper(this, R.style.Theme_Philotes);
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_button, null);

        iconView = floatingView.findViewById(R.id.floating_button);
        cardView = floatingView.findViewById(R.id.card_result);
        tvCardContent = floatingView.findViewById(R.id.tv_card_content);
        View btnClose = floatingView.findViewById(R.id.btn_close_card);
        View btnAction = floatingView.findViewById(R.id.btn_action);

        btnClose.setOnClickListener(v -> showIconMode());
        btnAction.setOnClickListener(v -> {
            Toast.makeText(this, "动作已执行 (Mock)", Toast.LENGTH_SHORT).show();
            showIconMode();
        });

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        try {
            windowManager.addView(floatingView, params);
            isFloatingViewAdded = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating view", e);
        }

        iconView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long startTime;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTime = System.currentTimeMillis();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - startTime;
                        if (duration < 200) {
                            onFloatingButtonClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showIconMode() {
        if (floatingView == null) return;
        iconView.setVisibility(View.VISIBLE);
        cardView.setVisibility(View.GONE);
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        windowManager.updateViewLayout(floatingView, params);
    }

    private void showCardMode(String content) {
        if (floatingView == null) return;
        tvCardContent.setText(content);
        iconView.setVisibility(View.GONE);
        cardView.setVisibility(View.VISIBLE);
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        windowManager.updateViewLayout(floatingView, params);
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Philotes助手")
                .setContentText("服务已就绪")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Button Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void onFloatingButtonClick() {
        floatingView.setVisibility(View.GONE);
        new Handler(Looper.getMainLooper()).postDelayed(this::performCapture, 100);
    }

    private void performCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull ScreenshotResult result) {
                    try {
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                        if (bitmap != null) {
                             // 必须复制 Bitmap，因为 HardwareBuffer 会被 close
                             Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                             result.getHardwareBuffer().close();
                             processBitmap(copy);
                        } else {
                             recoverFloatingView();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Screenshot processing failed", e);
                        recoverFloatingView();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e(TAG, "Screenshot failed with code: " + errorCode);
                    Toast.makeText(FloatingButtonService.this, "截屏失败", Toast.LENGTH_SHORT).show();
                    recoverFloatingView();
                }
            });
        } else {
            Toast.makeText(this, "当前系统版本不支持直接分析截图", Toast.LENGTH_SHORT).show();
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            recoverFloatingView();
        }
    }

    private void processBitmap(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "screenshot_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
            processAndShowCard(file);
        } catch (IOException e) {
            e.printStackTrace();
            recoverFloatingView();
        } finally {
            bitmap.recycle();
        }
    }

    private void recoverFloatingView() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (floatingView != null) floatingView.setVisibility(View.VISIBLE);
        });
    }

    private void processAndShowCard(File imageFile) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Toast.makeText(this, "正在分析截图...", Toast.LENGTH_SHORT).show();
            // Mock Analysis
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    String mockResult = "识别结果：\n\n- 时间：明天下午三点\n- 事件：项目复盘会\n- 地点：会议室A";
                    new Handler(Looper.getMainLooper()).post(() -> showCardMode(mockResult));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }, 200);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && isFloatingViewAdded) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {}
        }
    }

    // AccessibilityService doesn't use onBind like a regular service for other apps
    // But it's final in AccessibilityService so we don't override it improperly.
    // The super class handles it.
}
