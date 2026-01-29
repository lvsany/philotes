package com.example.philotes.utils;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.philotes.data.model.OcrResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class MlKitOcrService {
    private static final String TAG = "MlKitOcrService";
    private static TextRecognizer recognizer;

    private static TextRecognizer getRecognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        return recognizer;
    }

    public static void recognizeTextAsync(Bitmap bitmap, final OcrCallback callback) {
        if (bitmap == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Bitmap is null"));
            return;
        }

        final OcrResult result = new OcrResult(bitmap.getWidth(), bitmap.getHeight());
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        getRecognizer().process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        for (Text.TextBlock block : text.getTextBlocks()) {
                            String blockText = block.getText();
                            Rect boundingBox = block.getBoundingBox();
                            if (boundingBox != null && blockText != null && !blockText.trim().isEmpty()) {
                                result.addTextBlock(blockText, boundingBox, 0.9f);
                            }
                        }
                        Log.i(TAG, "OCR completed: " + result.getTextBlocks().size() + " blocks");
                        if (callback != null) callback.onSuccess(result);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "OCR failed", e);
                        if (callback != null) callback.onError(e);
                    }
                });
    }

    public static void close() {
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
    }

    public interface OcrCallback {
        void onSuccess(OcrResult result);
        void onError(Exception e);
    }
}
