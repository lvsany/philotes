package com.example.philotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.example.philotes.data.api.ILlmService;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.example.philotes.domain.ActionParser;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ActionParserTest {

    @Test
    public void parseStreaming_shouldEmitPartialAndCompletedPlan() throws Exception {
        String modelJson = "{\"type\":\"ADD_TODO\",\"slots\":{\"title\":\"买牛奶\",\"content\":\"买牛奶\"},\"confidence\":0.92,\"original_text\":\"买牛奶\"}";
        ILlmService fakeService = new FakeStreamingLlm(modelJson, 8);
        ActionParser parser = new ActionParser(fakeService);

        CountDownLatch done = new CountDownLatch(1);
        StringBuilder partial = new StringBuilder();
        final ActionPlan[] result = new ActionPlan[1];

        parser.parseStreaming("买牛奶", new ActionParser.ParseStreamListener() {
            @Override
            public void onStreamingText(String partialText) {
                partial.setLength(0);
                partial.append(partialText);
            }

            @Override
            public void onPlanCandidate(ActionPlan plan) {
                // candidate is optional for incomplete json chunks.
            }

            @Override
            public void onCompleted(ActionPlan plan) {
                result[0] = plan;
                done.countDown();
            }

            @Override
            public void onError(Exception error) {
                done.countDown();
            }
        });

        boolean finished = done.await(2, TimeUnit.SECONDS);
        assertTrueOrFail(finished, "streaming parse did not complete in time");
        assertFalse(partial.toString().isEmpty());
        assertNotNull(result[0]);
        assertEquals(ActionType.ADD_TODO, result[0].getType());
    }

    private static void assertTrueOrFail(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static class FakeStreamingLlm implements ILlmService {
        private final String payload;
        private final int chunkSize;

        FakeStreamingLlm(String payload, int chunkSize) {
            this.payload = payload;
            this.chunkSize = chunkSize;
        }

        @Override
        public String chatCompletion(String systemPrompt, String userMessage) {
            return payload;
        }

        @Override
        public void streamChatCompletion(String systemPrompt, String userMessage, StreamListener listener) {
            for (int i = 0; i < payload.length(); i += chunkSize) {
                int end = Math.min(payload.length(), i + chunkSize);
                listener.onDelta(payload.substring(i, end));
            }
            listener.onComplete();
        }
    }
}

