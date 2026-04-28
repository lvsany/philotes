package com.example.philotes.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.equationl.paddleocr4android.OCR;
import com.equationl.paddleocr4android.OcrConfig;
import com.equationl.paddleocr4android.Util.paddle.OcrResultModel;
import com.equationl.paddleocr4android.bean.OcrResult;
import com.equationl.paddleocr4android.callback.OcrInitCallback;
import com.equationl.paddleocr4android.callback.OcrRunCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * PaddleOCR-Lite service wrapper.
 *
 * Strategy:
 * 1) Try PaddleOCR first.
 * 2) Auto-download Paddle model files to app private storage if missing.
 * 3) If Paddle still fails, fall back to ML Kit OCR.
 */
public final class PaddleOcrService {
    private static final String TAG = "PaddleOcrService";
    private static final ExecutorService INIT_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String CUSTOM_ASSET_MODEL_DIR = "models/ch_PP-OCRv4";
    private static final String LOCAL_MODEL_REL_DIR = "paddle_ocr/ch_PP-OCRv4";

    private static final String MODEL_DET = "det.nb";
    private static final String MODEL_REC = "rec.nb";
    private static final String MODEL_CLS = "cls.nb";

    private static final String URL_DET = "https://raw.githubusercontent.com/equationl/paddleocr4android/master/app/src/main/assets/models/ch_PP-OCRv4/det.nb";
    private static final String URL_REC = "https://raw.githubusercontent.com/equationl/paddleocr4android/master/app/src/main/assets/models/ch_PP-OCRv4/rec.nb";
    private static final String URL_CLS = "https://raw.githubusercontent.com/equationl/paddleocr4android/master/app/src/main/assets/models/ch_PP-OCRv4/cls.nb";

    private static final long MIN_MODEL_BYTES = 100 * 1024; // 100KB

    private static OCR ocr;
    private static volatile boolean initializing = false;
    private static volatile boolean initialized = false;
    private static volatile boolean forceMlKitFallback = false;

    private PaddleOcrService() {
    }

    public interface OcrCallback {
        void onSuccess(com.example.philotes.data.model.OcrResult result);

        void onError(Exception e);
    }

    public static void recognizeTextAsync(Context context, Bitmap bitmap, OcrCallback callback) {
        if (bitmap == null) {
            if (callback != null)
                callback.onError(new IllegalArgumentException("Bitmap is null"));
            return;
        }
        if (context == null) {
            if (callback != null)
                callback.onError(new IllegalArgumentException("Context is null"));
            return;
        }

        Context appContext = context.getApplicationContext();
        if (forceMlKitFallback) {
            runMlKitFallback(appContext, bitmap, callback);
            return;
        }

        ensureInitialized(appContext, new InitCallback() {
            @Override
            public void onReady() {
                runOcr(appContext, bitmap, callback);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Paddle unavailable, switching to ML Kit fallback", e);
                runMlKitFallback(appContext, bitmap, callback);
            }
        });
    }

    public static synchronized void close() {
        if (ocr != null) {
            ocr.releaseModel();
            ocr = null;
        }
        initialized = false;
        initializing = false;
        forceMlKitFallback = false;
    }

    private static synchronized OCR getOrCreateOcr(Context appContext) {
        if (ocr == null) {
            ocr = new OCR(appContext);
        }
        return ocr;
    }

    private static void ensureInitialized(Context appContext, InitCallback callback) {
        if (initialized) {
            callback.onReady();
            return;
        }

        if (initializing) {
            INIT_EXECUTOR.execute(() -> waitForInit(callback));
            return;
        }

        initializing = true;
        OCR ocrEngine = getOrCreateOcr(appContext);

        INIT_EXECUTOR.execute(() -> {
            try {
                OcrConfig config = buildOcrConfigWithAutoDownload(appContext);
                new Handler(Looper.getMainLooper()).post(() -> startInit(ocrEngine, config, callback));
            } catch (Exception e) {
                initializing = false;
                initialized = false;
                forceMlKitFallback = true;
                callback.onError(new RuntimeException("Prepare Paddle model failed. ML Kit fallback enabled.", e));
            }
        });
    }

    private static void startInit(OCR ocrEngine, OcrConfig config, InitCallback callback) {
        ocrEngine.initModel(config, new OcrInitCallback() {
            @Override
            public void onSuccess() {
                initialized = true;
                initializing = false;
                Log.i(TAG, "PaddleOCR initialized successfully");
                callback.onReady();
            }

            @Override
            public void onFail(Throwable e) {
                initialized = false;
                initializing = false;
                forceMlKitFallback = true;
                Log.e(TAG, "PaddleOCR init failed. ML Kit fallback enabled.", e);
                callback.onError(new RuntimeException("PaddleOCR init failed. ML Kit fallback enabled.", e));
            }
        });
    }

    private static OcrConfig buildOcrConfigWithAutoDownload(Context context) throws IOException {
        OcrConfig config = new OcrConfig();
        config.setRunDet(true);
        config.setRunCls(true);
        config.setRunRec(true);
        config.setDrwwTextPositionBox(false);

        File localDir = getLocalModelDir(context);
        if (hasLocalModels(localDir)) {
            applyModelConfig(config, localDir.getAbsolutePath(), MODEL_DET, MODEL_REC, MODEL_CLS);
            Log.i(TAG, "Using local Paddle model from " + localDir.getAbsolutePath());
            return config;
        }

        if (hasAssetModels(context)) {
            applyModelConfig(config, CUSTOM_ASSET_MODEL_DIR, MODEL_DET, MODEL_REC, MODEL_CLS);
            Log.i(TAG, "Using asset Paddle model from " + CUSTOM_ASSET_MODEL_DIR);
            return config;
        }

        Log.i(TAG, "Paddle model missing. Start auto-download...");
        downloadLocalModels(localDir);

        if (hasLocalModels(localDir)) {
            applyModelConfig(config, localDir.getAbsolutePath(), MODEL_DET, MODEL_REC, MODEL_CLS);
            Log.i(TAG, "Using downloaded Paddle model from " + localDir.getAbsolutePath());
            return config;
        }

        Log.w(TAG, "Downloaded Paddle model verification failed, fallback to library default config");
        return config;
    }

    private static void applyModelConfig(OcrConfig config, String modelPath, String det, String rec, String cls) {
        config.setModelPath(modelPath);
        config.setDetModelFilename(det);
        config.setRecModelFilename(rec);
        config.setClsModelFilename(cls);
    }

    private static File getLocalModelDir(Context context) {
        return new File(context.getFilesDir(), LOCAL_MODEL_REL_DIR);
    }

    private static boolean hasLocalModels(File modelDir) {
        return validModelFile(new File(modelDir, MODEL_DET))
                && validModelFile(new File(modelDir, MODEL_REC))
                && validModelFile(new File(modelDir, MODEL_CLS));
    }

    private static boolean hasAssetModels(Context context) {
        return assetExists(context, CUSTOM_ASSET_MODEL_DIR + "/" + MODEL_DET)
                && assetExists(context, CUSTOM_ASSET_MODEL_DIR + "/" + MODEL_REC)
                && assetExists(context, CUSTOM_ASSET_MODEL_DIR + "/" + MODEL_CLS);
    }

    private static boolean assetExists(Context context, String path) {
        try (InputStream ignored = context.getAssets().open(path)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validModelFile(File file) {
        return file.exists() && file.length() >= MIN_MODEL_BYTES;
    }

    private static void downloadLocalModels(File modelDir) throws IOException {
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            throw new IOException("Create model directory failed: " + modelDir.getAbsolutePath());
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        downloadOne(client, URL_DET, new File(modelDir, MODEL_DET));
        downloadOne(client, URL_REC, new File(modelDir, MODEL_REC));
        downloadOne(client, URL_CLS, new File(modelDir, MODEL_CLS));
    }

    private static void downloadOne(OkHttpClient client, String url, File target) throws IOException {
        if (validModelFile(target)) {
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Philotes-Android/1.0")
                .header("Accept", "application/octet-stream,*/*")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Download failed: HTTP " + response.code() + " for " + url);
            }

            File tmp = new File(target.getAbsolutePath() + ".part");
            try (InputStream is = response.body().byteStream(); FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.flush();
            }

            if (!tmp.exists() || tmp.length() < MIN_MODEL_BYTES) {
                // noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("Downloaded file too small for " + url);
            }

            if (target.exists() && !target.delete()) {
                throw new IOException("Delete old model file failed: " + target.getAbsolutePath());
            }
            if (!tmp.renameTo(target)) {
                throw new IOException("Rename model file failed: " + target.getAbsolutePath());
            }
            Log.i(TAG, "Downloaded model: " + target.getName() + " (" + target.length() + " bytes)");
        }
    }

    private static void waitForInit(InitCallback callback) {
        int retry = 0;
        while (initializing && retry < 800) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
            retry++;
        }

        if (initialized) {
            callback.onReady();
        } else {
            callback.onError(new IllegalStateException("PaddleOCR initialization timeout"));
        }
    }

    private static void runMlKitFallback(Context context, Bitmap bitmap, OcrCallback callback) {
        MlKitOcrService.recognizeTextAsync(context, bitmap, new MlKitOcrService.OcrCallback() {
            @Override
            public void onSuccess(com.example.philotes.data.model.OcrResult result) {
                if (callback != null)
                    callback.onSuccess(result);
            }

            @Override
            public void onError(Exception e) {
                if (callback != null)
                    callback.onError(e);
            }
        });
    }

    private static void runOcr(Context context, Bitmap bitmap, OcrCallback callback) {
        OCR ocrEngine = ocr;
        if (ocrEngine == null) {
            if (callback != null)
                callback.onError(new IllegalStateException("PaddleOCR is not initialized"));
            return;
        }

        ocrEngine.run(bitmap, new OcrRunCallback() {
            @Override
            public void onSuccess(OcrResult paddleResult) {
                com.example.philotes.data.model.OcrResult mapped = new com.example.philotes.data.model.OcrResult(
                        bitmap.getWidth(), bitmap.getHeight());

                List<OcrResultModel> raw = paddleResult.getOutputRawResult();
                if (raw != null) {
                    for (OcrResultModel block : raw) {
                        String text = block.getLabel();
                        Rect rect = pointsToRect(block.getPoints(), bitmap.getWidth(), bitmap.getHeight());
                        if (text != null && !text.trim().isEmpty() && rect != null) {
                            mapped.addTextBlock(text, rect, block.getConfidence());
                        }
                    }
                }

                if (mapped.getTextBlocks().isEmpty()) {
                    String simpleText = paddleResult.getSimpleText();
                    if (simpleText != null && !simpleText.trim().isEmpty()) {
                        mapped.addTextBlock(simpleText, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), 1.0f);
                    }
                }

                Log.i(TAG, "PaddleOCR completed: " + mapped.getTextBlocks().size() + " blocks");
                if (callback != null)
                    callback.onSuccess(mapped);
            }

            @Override
            public void onFail(Throwable e) {
                Log.e(TAG, "Paddle OCR run failed, fallback to ML Kit", e);
                forceMlKitFallback = true;
                runMlKitFallback(context, bitmap, callback);
            }
        });
    }

    private static Rect pointsToRect(List<Point> points, int imageWidth, int imageHeight) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (Point p : points) {
            left = Math.min(left, p.x);
            top = Math.min(top, p.y);
            right = Math.max(right, p.x);
            bottom = Math.max(bottom, p.y);
        }

        left = clamp(left, 0, Math.max(imageWidth - 1, 0));
        right = clamp(right, 0, Math.max(imageWidth - 1, 0));
        top = clamp(top, 0, Math.max(imageHeight - 1, 0));
        bottom = clamp(bottom, 0, Math.max(imageHeight - 1, 0));

        if (right <= left || bottom <= top) {
            return null;
        }

        return new Rect(left, top, right, bottom);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface InitCallback {
        void onReady();

        void onError(Exception e);
    }
}
