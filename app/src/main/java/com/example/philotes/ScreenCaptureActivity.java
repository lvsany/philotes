package com.example.philotes;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ScreenCaptureActivity extends AppCompatActivity {

    private static final String TAG = "ScreenCaptureActivity";
    private static final int REQUEST_CODE_CAPTURE_PERM = 1001;
    private static final int RESULT_CODE_INVALID = Integer.MIN_VALUE;
    private MediaProjectionManager projectionManager;
    
    // 静态变量暂存 MediaProjection 授权结果，供 Service 获取
    // 注意：RESULT_OK = -1，所以不能用 -1 作为无效值
    private static int sResultCode = RESULT_CODE_INVALID;
    private static Intent sResultData = null;

    public static boolean hasValidResult() {
        return sResultCode != RESULT_CODE_INVALID && sResultData != null;
    }

    public static int getResultCode() {
        return sResultCode;
    }

    public static Intent getResultData() {
        return sResultData;
    }

    public static void clearResult() {
        sResultCode = RESULT_CODE_INVALID;
        sResultData = null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 请求录屏权限
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PERM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CAPTURE_PERM) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "Screen capture permission granted");
                
                // 保存授权结果到静态变量
                sResultCode = resultCode;
                sResultData = data;

                try {
                    // 启动前台服务
                    Intent serviceIntent = new Intent(this, FloatingButtonService.class);
                    serviceIntent.setAction(FloatingButtonService.ACTION_INIT_PROJECTION);
                    ContextCompat.startForegroundService(this, serviceIntent);
                    
                    // 延迟关闭 Activity，确保 Service 有时间获取 MediaProjection
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 500);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start service", e);
                    Toast.makeText(this, "启动服务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    clearResult();
                    finish();
                }
            } else {
                Toast.makeText(this, "截屏权限被拒绝", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
