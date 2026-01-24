package com.example.philotes.utils;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelUtils {
    private static final String TAG = "ModelUtils";
    // The name of the model file as it would appear in assets or storage
    public static final String MODEL_NAME = "gemma-2b-it-gpu-int4.bin";

    // TODO: Replace this with your own DIRECT download link.
    // GUIDANCE:
    // 1. You can verify the link in your browser: it must start downloading the .bin file immediately,
    //    not open a preview page (like Google Drive website).
    // 2. Recommended free hosting for testing: GitHub Releases (upload .bin as an asset).
    // 3. Example format: "https://myserver.com/models/gemma-2b-it-gpu-int4.bin"
    public static final String MODEL_URL = "https://github.com/lvsany/gamma_model/releases/download/v1.0/gemma-2b-it-gpu-int4.bin";

    public interface DownloadListener {
        void onProgress(int percentage);
        void onCompleted(File file);
        void onError(Exception e);
    }

    /**
     * Prepares the model file for use.
     * 1. Checks if the model exists in the app's private files directory (Production/Scheme 2).
     * 2. Checks if the model exists in /data/local/tmp/ (Development/Scheme 1).
     * 3. Checks assets.
     */
    public static File getModelFile(Context context) {
        // Priority 1: Internal Storage (App Private)
        File folder = context.getFilesDir();
        File modelFile = new File(folder, MODEL_NAME);

        // Strict check: Model must be > 10MB (Gemma is ~1.2GB).
        // Prevents treating 404 HTML pages or empty files as valid models.
        if (modelFile.exists()) {
            if (modelFile.length() > 10 * 1024 * 1024) {
                return modelFile;
            } else {
                Log.w(TAG, "Found incomplete/small model file (" + modelFile.length() + " bytes). Deleting to force redownload.");
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

        // Default: Return the Internal Storage path so download/push instructions point there
        return modelFile;
    }

    public static void downloadModel(Context context, String url, File targetFile, DownloadListener listener) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                listener.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onError(new IOException("Unexpected code " + response));
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
                    listener.onCompleted(targetFile);
                } catch (Exception e) {
                    listener.onError(e);
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
