package com.example.philotes.utils;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelUtils {
    private static final String TAG = "ModelUtils";
    // Qwen3.5-0.8B LiteRT quantized model filename.
    public static final String MODEL_NAME = "qwen35_mm_q8_ekv2048.tflite";

    // TODO: Replace this with your own DIRECT .tflite download link.
    // GUIDANCE:
    // 1. You can verify the link in your browser: it must start downloading the
    // .tflite file immediately,
    // not open a preview page (like Google Drive website).
    // 2. Recommended free hosting for testing: GitHub Releases (upload .tflite as
    // an asset).
    // 3. Example format: "https://myserver.com/models/qwen35_mm_q8_ekv2048.tflite"
    public static final String MODEL_URL = "https://github.com/Anuan-shu/qw-model/releases/download/v1/qwen35_mm_q8_ekv2048.tflite";
    public static final String MODEL_URL_MIRROR = "https://github.com/Anuan-shu/qw-model/releases/download/v1/qwen35_mm_q8_ekv2048.tflite";

    public interface DownloadListener {
        void onProgress(int percentage);

        void onCompleted(File file);

        void onError(Exception e);
    }

    /**
     * Prepares the model file for use.
     * 1. Checks if the model exists in the app's private files directory
     * (Production/Scheme 2).
     * 2. Checks if the model exists in /data/local/tmp/ (Development/Scheme 1).
     * 3. Checks assets.
     */
    public static File getModelFile(Context context) {
        // Priority 1: Internal Storage (App Private)
        File folder = context.getFilesDir();
        File modelFile = new File(folder, MODEL_NAME);

        // Strict check: Model must be > 10MB.
        // Prevents treating 404 HTML pages or empty files as valid models.
        if (modelFile.exists()) {
            if (modelFile.length() > 10 * 1024 * 1024) {
                return modelFile;
            } else {
                Log.w(TAG, "Found incomplete/small model file (" + modelFile.length()
                        + " bytes). Deleting to force redownload.");
                modelFile.delete();
            }
        }

        // Priority 2: /data/local/tmp/ (Easier for ADB push)
        File tmpFile = new File("/data/local/tmp/" + MODEL_NAME);
        // Note: We probably can't delete files in /data/local/tmp/ from the app user,
        // so we just ignore it if it's too small.
        if (tmpFile.exists() && tmpFile.length() > 10 * 1024 * 1024) {
            Log.d(TAG, "Model found in /data/local/tmp");
            return tmpFile;
        }

        // Priority 3: Assets (bundled)
        try {
            InputStream is = context.getAssets().open(MODEL_NAME);
            Log.d(TAG, "Model found in assets. Copying to internal storage...");
            copyInputStreamToFile(is, modelFile);
            return modelFile;
        } catch (IOException e) {
            Log.w(TAG, "Model not found in assets.");
        }

        // Default: Return the Internal Storage path so download/push instructions point
        // there
        return modelFile;
    }

    public static void downloadModel(Context context, String url, File targetFile, DownloadListener listener) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        List<String> candidateUrls = new ArrayList<>();
        candidateUrls.add(url);
        if (MODEL_URL.equals(url)) {
            candidateUrls.add(MODEL_URL_MIRROR);
        }

        downloadWithFallback(client, candidateUrls, 0, targetFile, listener, null);
    }

    private static void downloadWithFallback(
            OkHttpClient client,
            List<String> urls,
            int index,
            File targetFile,
            DownloadListener listener,
            String lastError) {
        if (index >= urls.size()) {
            String msg = "All download URLs failed" + (lastError != null ? (": " + lastError) : "");
            listener.onError(new IOException(msg));
            return;
        }

        String currentUrl = urls.get(index);
        Log.i(TAG, "Downloading model from: " + currentUrl);

        Request request = new Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "Philotes-Android/1.0")
                .header("Accept", "application/octet-stream,*/*")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Download failed from " + currentUrl + ", trying next URL", e);
                downloadWithFallback(client, urls, index + 1, targetFile, listener, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    String err = "HTTP " + response.code() + " from " + currentUrl;
                    response.close();
                    Log.w(TAG, err + ", trying next URL");
                    downloadWithFallback(client, urls, index + 1, targetFile, listener, err);
                    return;
                }

                try (InputStream is = response.body().byteStream();
                        FileOutputStream fos = new FileOutputStream(targetFile)) {

                    long totalBytes = response.body().contentLength();
                    byte[] buffer = new byte[8192];
                    int len;
                    long bytesRead = 0;

                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        bytesRead += len;

                        if (totalBytes > 0) {
                            int progress = (int) ((bytesRead * 100) / totalBytes);
                            listener.onProgress(progress);
                        }
                    }
                    fos.flush();

                    if (targetFile.length() < 10 * 1024 * 1024) {
                        String err = "Downloaded file too small: " + targetFile.length() + " bytes";
                        Log.w(TAG, err + ", trying next URL");
                        // noinspection ResultOfMethodCallIgnored
                        targetFile.delete();
                        downloadWithFallback(client, urls, index + 1, targetFile, listener, err);
                        return;
                    }

                    listener.onCompleted(targetFile);
                } catch (Exception e) {
                    Log.w(TAG, "Download stream error from " + currentUrl + ", trying next URL", e);
                    // noinspection ResultOfMethodCallIgnored
                    targetFile.delete();
                    downloadWithFallback(client, urls, index + 1, targetFile, listener, e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private static void copyInputStreamToFile(InputStream in, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[1024 * 64]; // 64KB buffer
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            in.close();
        }
    }
}
