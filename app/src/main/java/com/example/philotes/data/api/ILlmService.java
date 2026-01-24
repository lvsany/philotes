package com.example.philotes.data.api;

/**
 * Common interface for LLM interactions.
 * Allows switching between Remote (OpenAI/Ollama) and Local (On-Device MediaPipe) implementations.
 */
public interface ILlmService {
    /**
     * Generates a response from the LLM based on a system prompt and user message.
     * @param systemPrompt The instruction for the model.
     * @param userMessage The input text to process.
     * @return The model's response text, or null if failed.
     */
    String chatCompletion(String systemPrompt, String userMessage);
}
