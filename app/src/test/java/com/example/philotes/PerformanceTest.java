package com.example.philotes;

import com.example.philotes.data.api.ILlmService;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.input.MultimodalInputCoordinator;
import com.example.philotes.domain.ActionExecutor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PerformanceTest {

    private static final String TEST_JSON = "{\"type\":\"ADD_TODO\",\"slots\":{\"title\":\"买牛奶\",\"content\":\"买牛奶\"},\"confidence\":0.92,\"original_text\":\"买牛奶\"}";

    @Test
    public void performanceMetrics_streamingParse() throws Exception {
        ILlmService fakeService = new FakeStreamingLlm(TEST_JSON, 8);
        ActionParser parser = new ActionParser(fakeService);

        System.out.println("\n========== 性能测试：流式解析 ==========");

        // 预热
        CountDownLatch warmup = new CountDownLatch(1);
        parser.parseStreaming("测试", new ActionParser.ParseStreamListener() {
            @Override public void onStreamingText(String partialText) {}
            @Override public void onPlanCandidate(ActionPlan plan) {}
            @Override public void onCompleted(ActionPlan plan) { warmup.countDown(); }
            @Override public void onError(Exception error) { warmup.countDown(); }
        });
        warmup.await(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // 实际测试
        long startTime = System.currentTimeMillis();
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            CountDownLatch done = new CountDownLatch(1);
            parser.parseStreaming("买牛奶", new ActionParser.ParseStreamListener() {
                @Override
                public void onStreamingText(String partialText) {}
                @Override
                public void onPlanCandidate(ActionPlan plan) {}
                @Override
                public void onCompleted(ActionPlan plan) {
                    done.countDown();
                }
                @Override
                public void onError(Exception error) {
                    done.countDown();
                }
            });
            done.await(5, TimeUnit.SECONDS);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;

        System.out.printf("执行次数: %d\n", iterations);
        System.out.printf("总耗时: %d ms\n", totalTime);
        System.out.printf("平均耗时: %.2f ms\n", avgTime);
        System.out.printf("吞吐量: %.2f req/s\n", (iterations * 1000.0) / totalTime);
        System.out.printf("JSON大小: %d bytes\n", TEST_JSON.getBytes().length);

        // GC统计
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        System.out.printf("内存占用: %d MB\n", usedMemory / (1024 * 1024));
    }

    @Test
    public void performanceMetrics_concurrentParsing() throws Exception {
        ActionParser parser = new ActionParser(new FakeStreamingLlm(TEST_JSON, 8));

        System.out.println("\n========== 性能测试：并发解析 ==========");

        int threadCount = 10;
        int requestsPerThread = 10;
        List<Thread> threads = new ArrayList<>();
        long[] timings = new long[threadCount];

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                long threadStart = System.currentTimeMillis();
                for (int i = 0; i < requestsPerThread; i++) {
                    CountDownLatch done = new CountDownLatch(1);
                    parser.parseStreaming("买牛奶", new ActionParser.ParseStreamListener() {
                        @Override public void onStreamingText(String partialText) {}
                        @Override public void onPlanCandidate(ActionPlan plan) {}
                        @Override public void onCompleted(ActionPlan plan) { done.countDown(); }
                        @Override public void onError(Exception error) { done.countDown(); }
                    });
                    try {
                        done.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                timings[threadId] = System.currentTimeMillis() - threadStart;
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int totalRequests = threadCount * requestsPerThread;

        System.out.printf("线程数: %d\n", threadCount);
        System.out.printf("总请求数: %d\n", totalRequests);
        System.out.printf("总耗时: %d ms\n", totalTime);
        System.out.printf("并发吞吐量: %.2f req/s\n", (totalRequests * 1000.0) / totalTime);
        System.out.printf("平均响应时间: %.2f ms\n", (double) totalTime / totalRequests);

        long avgThreadTime = 0;
        for (long t : timings) avgThreadTime += t;
        avgThreadTime /= threadCount;
        System.out.printf("平均线程耗时: %d ms\n", avgThreadTime);
    }

    @Test
    public void performanceMetrics_dataFlow() throws Exception {
        System.out.println("\n========== 性能测试：数据流和流量分析 ==========");

        // LLM请求大小
        String userMessage = "明天3点开会";
        String systemPrompt = "You are a helpful assistant";
        int requestSize = userMessage.getBytes().length + systemPrompt.getBytes().length;

        System.out.printf("用户输入: %s (%d bytes)\n", userMessage, userMessage.getBytes().length);
        System.out.printf("系统提示词: %d bytes\n", systemPrompt.getBytes().length);
        System.out.printf("请求总大小: %d bytes (%.2f KB)\n", requestSize, requestSize / 1024.0);

        // LLM响应大小
        String response = TEST_JSON;
        int responseSize = response.getBytes().length;
        System.out.printf("\nLLM响应: %s\n", response);
        System.out.printf("响应大小: %d bytes (%.2f KB)\n", responseSize, responseSize / 1024.0);

        // 单次交互总流量
        int totalTraffic = requestSize + responseSize;
        System.out.printf("\n单次交互总流量: %d bytes (%.2f KB)\n", totalTraffic, totalTraffic / 1024.0);

        // 100次交互的预估流量
        int hundredRequests = totalTraffic * 100;
        System.out.printf("100次交互总流量: %d bytes (%.2f KB, %.2f MB)\n",
                hundredRequests, hundredRequests / 1024.0, hundredRequests / (1024.0 * 1024.0));

        // 流式处理的分块大小
        System.out.printf("\n流式处理分块大小: 8 bytes\n");
        System.out.printf("分块数: %d\n", responseSize / 8);
        System.out.printf("流式传输总大小: %d bytes (相同)\n", responseSize);

        // 估算带宽消耗
        long responseTime = 500; // ms
        double bandwidth = (responseSize * 1000.0) / (responseTime * 1024); // KB/s
        System.out.printf("\n假设响应时间: %d ms\n", responseTime);
        System.out.printf("估算带宽消耗: %.2f KB/s\n", bandwidth);
    }

    @Test
    public void performanceMetrics_memoryUsage() throws Exception {
        System.out.println("\n========== 性能测试：内存占用 ==========");

        Runtime runtime = Runtime.getRuntime();

        // 初始内存
        System.gc();
        Thread.sleep(100);
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("初始内存占用: %d MB\n", initialMemory / (1024 * 1024));

        // 创建Parser
        ActionParser parser = new ActionParser(new FakeStreamingLlm(TEST_JSON, 8));
        long parserMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("Parser创建后: %d MB (+%d MB)\n",
                parserMemory / (1024 * 1024),
                (parserMemory - initialMemory) / (1024 * 1024));

        // 执行100次解析
        for (int i = 0; i < 100; i++) {
            CountDownLatch done = new CountDownLatch(1);
            parser.parseStreaming("买牛奶", new ActionParser.ParseStreamListener() {
                @Override public void onStreamingText(String partialText) {}
                @Override public void onPlanCandidate(ActionPlan plan) {}
                @Override public void onCompleted(ActionPlan plan) { done.countDown(); }
                @Override public void onError(Exception error) { done.countDown(); }
            });
            try {
                done.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {}
        }

        long afterParsingMemory = runtime.totalMemory() - runtime.freeMemory();
        System.gc();
        Thread.sleep(100);
        long afterGCMemory = runtime.totalMemory() - runtime.freeMemory();

        System.out.printf("100次解析后: %d MB (+%d MB)\n",
                afterParsingMemory / (1024 * 1024),
                (afterParsingMemory - parserMemory) / (1024 * 1024));
        System.out.printf("GC后: %d MB\n", afterGCMemory / (1024 * 1024));

        // 峰值内存
        long peakMemory = runtime.maxMemory();
        System.out.printf("最大可用内存: %d MB\n", peakMemory / (1024 * 1024));
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

