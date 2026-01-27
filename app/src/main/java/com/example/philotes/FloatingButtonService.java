package com.example.philotes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FloatingButtonService extends Service {
    private static final String CHANNEL_ID = "FloatingButtonServiceChannel";
    public static final String ACTION_INIT_PROJECTION = "com.example.philotes.INIT_PROJECTION";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "data";

    private WindowManager windowManager;
    private View floatingView;
    private View iconView;
    private View cardView;
    private TextView tvCardContent;
    private WindowManager.LayoutParams params;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private boolean isFloatingViewAdded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // 注意：不要在 onCreate 中添加悬浮窗
        // 必须等到 startForeground() 调用后才能添加
        // 悬浮窗的初始化移到 initFloatingView() 方法中

        // 获取屏幕数据
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    /**
     * 初始化悬浮窗视图
     * 必须在 startForeground() 之后调用
     */
    private void initFloatingView() {
        if (isFloatingViewAdded) return;

        // 使用带有主题的 Context 来 inflate 布局，避免主题属性无法解析
        Context themedContext = new android.view.ContextThemeWrapper(this, R.style.Theme_Philotes);
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_button, null);

        // 初始化 View 引用
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
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
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
            Log.e("FloatingService", "Failed to add floating view", e);
        }

        // 只有在 Icon 模式下才响应拖拽
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
                            // Click detected
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
        // 使用之前记录的 params，或者只更新宽高
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        // 注意：这里可能需要保留 x, y 位置，不要重置
        windowManager.updateViewLayout(floatingView, params);
    }

    private void showCardMode(String content) {
        if (floatingView == null) return;

        // 更新内容
        tvCardContent.setText(content);

        // 切换显示
        iconView.setVisibility(View.GONE);
        cardView.setVisibility(View.VISIBLE);

        // 调整 Window 大小以适应卡片
        // params.width = WindowManager.LayoutParams.MATCH_PARENT; // 或者固定宽度
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        windowManager.updateViewLayout(floatingView, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 防止 intent 为空导致 crash
        if (intent == null) {
            // Service restarted by system without intent (START_STICKY), but we need the projection data.
            // Cannot start foreground with mediaProjection type without permission.
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_INIT_PROJECTION.equals(intent.getAction())) {
            // 从 ScreenCaptureActivity 的静态变量获取授权结果
            int resultCode = ScreenCaptureActivity.getResultCode();
            Intent data = ScreenCaptureActivity.getResultData();
            
            Log.d("FloatingService", "onStartCommand: resultCode=" + resultCode + ", data=" + data);
            
            if (ScreenCaptureActivity.hasValidResult()) {
                try {
                    // Start foreground service with MEDIA_PROJECTION type
                    // 必须先调用 startForeground()，然后才能进行其他操作
                    if (Build.VERSION.SDK_INT >= 29) {
                        int type = 0;
                        if (Build.VERSION.SDK_INT >= 34) {
                            type = 32; // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        } else {
                            type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                        }
                        startForeground(1, createNotification(), type);
                    } else {
                        startForeground(1, createNotification());
                    }

                    // 在 startForeground() 之后初始化悬浮窗
                    initFloatingView();

                    mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    
                    // 清除静态变量，避免内存泄漏
                    ScreenCaptureActivity.clearResult();
                    
                    if (mediaProjection == null) {
                        Log.e("FloatingService", "Failed to get MediaProjection, data may be invalid");
                        Toast.makeText(this, "获取截屏权限失败，请重试", Toast.LENGTH_SHORT).show();
                        stopSelf();
                        return START_NOT_STICKY;
                    }
                    
                    // 注册回调，监听 MediaProjection 停止，例如被系统收回
                    mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            mediaProjection = null;
                            stopVirtualDisplay();
                        }
                    }, null);

                    Toast.makeText(this, "悬浮截屏服务已启动，点击悬浮球截图", Toast.LENGTH_SHORT).show();
                    // 不要立即截图，等待用户点击
                    // performCapture();

                } catch (Exception e) {
                    Log.e("FloatingService", "Failed to start service or get projection", e);
                    Toast.makeText(this, "启动服务失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    ScreenCaptureActivity.clearResult();
                    stopSelf();
                }
            } else {
                Log.e("FloatingService", "Invalid resultCode or data is null");
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Snap2Action正在运行")
                .setContentText("悬浮窗服务已启动")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Button Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void onFloatingButtonClick() {
        // 先隐藏悬浮窗，避免遮挡
        floatingView.setVisibility(View.GONE);

        // 延迟一小会儿确保 View 已隐藏，再截图
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (mediaProjection != null) {
                performCapture();
            } else {
                // 这里理论上不应该走到，因为启动服务时就已经强制要求了权限。
                // 但如果 App 被杀后重启 Service（START_NOT_STICKY），mediaProjection 会丢失。
                // 此时需要重新请求。
                Toast.makeText(this, "需要重新获取截屏权限...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, ScreenCaptureActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }, 100); // 100ms 足够隐藏
    }

    private void performCapture() {
        if (mediaProjection == null) return;

        // 如果 ImageReader 尚未创建，则创建
        if (imageReader == null) {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        }

        // 创建 VirtualDisplay 开始具体的录制
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * screenWidth;

                    bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    Bitmap finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);

                    File cachePath = new File(getCacheDir(), "images");
                    cachePath.mkdirs();
                    File file = new File(cachePath, "screenshot_" + System.currentTimeMillis() + ".png");
                    fos = new FileOutputStream(file);
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

                    Log.d("FloatingService", "Screenshot saved: " + file.getAbsolutePath());
                    // 以前是 openMainActivity(file);
                    // 现在改为处理并显示卡片
                    processAndShowCard(file);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(FloatingButtonService.this, "截屏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                if (fos != null) {
                    try { fos.close(); } catch (IOException e) { e.printStackTrace(); }
                }
                if (bitmap != null) bitmap.recycle();
                if (image != null) image.close();

                stopVirtualDisplay();
                // 恢复悬浮窗
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (floatingView != null) floatingView.setVisibility(View.VISIBLE);
                });
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void stopVirtualDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            // 注意：不要在这里close imageReader，因为它可能被重用。
            // 除非你想每次截图都重新创建ImageReader
            imageReader.setOnImageAvailableListener(null, null);
        }
    }

    private void processAndShowCard(File imageFile) {
        // 恢复悬浮窗显示（先恢复成 hidden 状态，然后 showCardMode 会处理 visibility）
        // 注意：performCapture 的 finally 块里有一个恢复显示的逻辑，我们需要覆盖它或者利用它
        // 这里我们可以先模拟后台处理

        // 我们最好在 finally 块里只是简单的 "setVisibility(VISIBLE)"，
        // 但是通过 postDelayed 来更新状态可能会跟 finally 里的冲突。
        // 最好是修改 finally 里的逻辑，或者在这里控制。

        // 实际上 finally 里的逻辑是：
        // new Handler(Looper.getMainLooper()).post(() -> {
        //    if (floatingView != null) floatingView.setVisibility(View.VISIBLE);
        // });

        // 所以我们可以在这里安排一个任务，在 finally 那个恢复之后执行
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
             // 模拟分析耗时
            Toast.makeText(this, "正在分析截图...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                try {
                    Thread.sleep(1500); // 模拟耗时
                    String mockResult = "识别结果：\n\n- 时间：明天下午三点\n- 事件：项目复盘会\n- 地点：会议室A";

                    runOnUiThread(() -> {
                        showCardMode(mockResult);
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }, 200); // 稍微晚一点，覆盖 finally 的显示
    }

    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && isFloatingViewAdded) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.e("FloatingService", "Failed to remove floating view", e);
            }
            isFloatingViewAdded = false;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
