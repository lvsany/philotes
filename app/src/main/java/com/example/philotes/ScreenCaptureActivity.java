package com.example.philotes;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ScreenCaptureActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_CAPTURE_PERM = 1001;
    private MediaProjectionManager projectionManager;

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
                // 将权限结果传递给 Service
                Intent serviceIntent = new Intent(this, FloatingButtonService.class);
                serviceIntent.setAction(FloatingButtonService.ACTION_INIT_PROJECTION);
                serviceIntent.putExtra(FloatingButtonService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(FloatingButtonService.EXTRA_DATA, data);

                // 使用 startForegroundService 启动
                ContextCompat.startForegroundService(this, serviceIntent);

                finish();
            } else {
                Toast.makeText(this, "截屏权限被拒绝", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
