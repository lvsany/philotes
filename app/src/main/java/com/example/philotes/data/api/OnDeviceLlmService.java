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
            // NOTE: This usually catches the case where the file hasn't been pushed or copied yet.
            // MainActivity displays a warning, but if we try to run anyway:
            throw new RuntimeException("Model file MISSING at path: " + modelPath +
                "\nSee app screen for instructions.");
        }

        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                // .setMaxTokens(512)
                // .setResultThreshold(0.5f)
                .build();

        try {
            llmInference = LlmInference.createFromOptions(context, options);
        } catch (Throwable t) {
            String msg = "Failed to initialize On-Device LLM. \n" +
                         "If you are running on an Emulator, this is expected (MediaPipe GenAI supports ARM64 devices only).\n" +
                         "Error: " + t.getMessage();
            System.err.println(msg);
            throw new RuntimeException(msg, t);
        }
    }

    @Override
    public String chatCompletion(String systemPrompt, String userMessage) {
        if (llmInference == null) {
            initialize();
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

        System.out.println("LLM Input: " + fullPrompt);

        try {
            String result = llmInference.generateResponse(fullPrompt);
            System.out.println("LLM Output: " + result);
            return result;
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
