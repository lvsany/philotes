package com.example.philotes.data.api;

/**
 * Common interface for LLM interactions.
 * Allows switching between Remote (OpenAI/Ollama) and Local (On-Device MediaPipe) implementations.
 */
public interface ILlmService {
    interface StreamListener {
        void onDelta(String delta);
        void onComplete();
        void onError(Exception error);
    }

    /**
     * Generates a response from the LLM based on a system prompt and user message.
     * @param systemPrompt The instruction for the model.
     * @param userMessage The input text to process.
     * @return The model's response text, or null if failed.
     */
    String chatCompletion(String systemPrompt, String userMessage);

    /**
     * Optional streaming response; default behavior chunks full output to keep existing services compatible.
     */
    default void streamChatCompletion(String systemPrompt, String userMessage, StreamListener listener) {
        try {
            String fullResponse = chatCompletion(systemPrompt, userMessage);
            if (fullResponse == null) {
                listener.onError(new IllegalStateException("LLM returned empty response"));
                return;
            }

            final int chunkSize = 24;
            for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                int end = Math.min(fullResponse.length(), i + chunkSize);
                listener.onDelta(fullResponse.substring(i, end));
            }
            listener.onComplete();
        } catch (Exception e) {
            listener.onError(e);
        }
    }
}
