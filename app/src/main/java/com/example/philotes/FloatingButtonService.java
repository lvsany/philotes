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
    private WindowManager.LayoutParams params;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification());

        // 获取屏幕数据
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null);
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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingView, params);
        ImageView closeButton = floatingView.findViewById(R.id.floating_button);
        closeButton.setOnTouchListener(new View.OnTouchListener() {
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_INIT_PROJECTION.equals(intent.getAction())) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            if (resultCode != -1 && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                // 注册回调，监听 MediaProjection 停止，例如被系统收回
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        super.onStop();
                        mediaProjection = null;
                        stopVirtualDisplay();
                    }
                }, null);

                // 立即进行截图
                performCapture();
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
                Toast.makeText(this, "正在请求截屏权限...", Toast.LENGTH_SHORT).show();
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
                    openMainActivity(file);
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

    private void openMainActivity(File imageFile) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra("image_path", imageFile.getAbsolutePath());
        intent.putExtra(Intent.EXTRA_TEXT, "PROCESSED_BY_SERVICE"); // 标记位
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
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
