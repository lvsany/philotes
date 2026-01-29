package com.example.philotes.data.api;

import android.content.Context;
import android.util.Log;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

/**
 * On-Device LLM interpretation using Google MediaPipe GenAI.
 * Completely offline and private.
 * Requires a downloaded model file (e.g. gemma-2b-it-gpu-int4.bin).
 */
public class OnDeviceLlmService implements ILlmService {
    private static final String TAG = "OnDeviceLlmService";
    private final Context context;
    private final String modelPath;
    private LlmInference llmInference;
    private boolean initializationFailed = false;
    private String failureReason = "";

    // Provide a default path like "/data/local/tmp/gemma-2b.bin" or copy from assets.
    public OnDeviceLlmService(Context context, String modelPath) {
        this.context = context;
        this.modelPath = modelPath;
    }

    public void initialize() {
        if (llmInference != null) return;
        if (initializationFailed) return; // Don't retry if already failed

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            initializationFailed = true;
            failureReason = "Model file not found at: " + modelPath;
            Log.e(TAG, failureReason);
            throw new RuntimeException(failureReason + "\nPlease download the model first.");
        }

        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                // .setMaxTokens(512)
                // .setResultThreshold(0.5f)
                .build();

        try {
            llmInference = LlmInference.createFromOptions(context, options);
            Log.i(TAG, "LLM initialized successfully");
        } catch (Throwable t) {
            initializationFailed = true;
            failureReason = t.getMessage();
            String msg = "Failed to initialize On-Device LLM.\n" +
                         "If you are running on an Emulator, this is expected (MediaPipe GenAI supports ARM64 devices only).\n" +
                         "Error: " + failureReason;
            Log.e(TAG, msg, t);
            // Don't throw - allow app to continue without LLM
        }
    }

    @Override
    public String chatCompletion(String systemPrompt, String userMessage) {
        if (initializationFailed) {
            Log.e(TAG, "Cannot complete chat - initialization failed: " + failureReason);
            return null;
        }

        if (llmInference == null) {
            try {
                initialize();
            } catch (Exception e) {
                Log.e(TAG, "Initialization failed during chat completion", e);
                return null;
            }
            if (llmInference == null) {
                return null; // Initialization failed
            }
        }

        // Format prompt for Gemma:
        // <start_of_turn>user
        // {system_prompt}
        //
        // {user_message}<end_of_turn>
        // <start_of_turn>model
        String fullPrompt = "<start_of_turn>user\n" +
                            systemPrompt + "\n\n" +
                            "Input text:\n" + userMessage + "<end_of_turn>\n" +
                            "<start_of_turn>model\n";

        Log.d(TAG, "LLM Input: " + fullPrompt);

        try {
            String result = llmInference.generateResponse(fullPrompt);
            Log.d(TAG, "LLM Output: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error generating LLM response", e);
            return null;
        }
    }

    public void close() {
        // LlmInference doesn't have a close method exposed directly in all versions,
        // but it's good practice to clear references.
        llmInference = null;
    }

    public boolean isInitialized() {
        return llmInference != null;
    }

    public boolean hasInitializationFailed() {
        return initializationFailed;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
