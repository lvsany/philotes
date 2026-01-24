package com.example.philotes.data.api;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

/**
 * On-Device LLM interpretation using Google MediaPipe GenAI.
 * Completely offline and private.
 * Requires a downloaded model file (e.g. gemma-2b-it-gpu-int4.bin).
 */
public class OnDeviceLlmService implements ILlmService {
    private final Context context;
    private final String modelPath;
    private LlmInference llmInference;

    // Provide a default path like "/data/local/tmp/gemma-2b.bin" or copy from assets.
    public OnDeviceLlmService(Context context, String modelPath) {
        this.context = context;
        this.modelPath = modelPath;
    }

    public void initialize() {
        if (llmInference != null) return;

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            System.err.println("OnDeviceLlmService: Model file not found at " + modelPath);
            // In a real app, you might copy from assets here.
            return;
        }

        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                // .setMaxTokens(512)
                // .setResultThreshold(0.5f)
                .build();

        llmInference = LlmInference.createFromOptions(context, options);
    }

    @Override
    public String chatCompletion(String systemPrompt, String userMessage) {
        if (llmInference == null) {
            initialize();
            if (llmInference == null) {
                return null; // Initialization failed
            }
        }

        // MediaPipe LlmInference usually takes a single prompt string.
        // We need to format it manually according to the model's template (e.g. Gemma format).
        // This is a simplified "ChatML" style or "Instruction" style concatenation.
        String fullPrompt = systemPrompt + "\n\nUser: " + userMessage + "\nModel:";

        try {
            return llmInference.generateResponse(fullPrompt);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void close() {
        // LlmInference doesn't have a close method exposed directly in all versions,
        // but it's good practice to clear references.
        llmInference = null;
    }
}
