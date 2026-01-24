package com.example.philotes.data.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIService implements ILlmService {
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final OkHttpClient client;
    private final Gson gson;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public OpenAIService(String apiKey) {
        this(apiKey, "https://api.openai.com/v1");
    }

    public OpenAIService(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, "gpt-3.5-turbo");
    }

    public OpenAIService(String apiKey, String baseUrl, String modelName) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public String chatCompletion(String systemPrompt, String userMessage) {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);

        JsonObject userMessageObj = new JsonObject();
        userMessageObj.addProperty("role", "user");
        userMessageObj.addProperty("content", userMessage);

        JsonArray messages = new JsonArray();
        messages.add(systemMessage);
        messages.add(userMessageObj);

        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.addProperty("model", modelName);
        requestBodyJson.add("messages", messages);
        requestBodyJson.addProperty("temperature", 0.0);

        RequestBody body = RequestBody.create(requestBodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("API Error: " + response.code() + " " + response.message());
                return null;
            }

            if (response.body() != null) {
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                    return jsonResponse.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
