package com.example.philotes.render;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.philotes.input.MultimodalInputCoordinator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Async rendering engine that emits card-render events as a data stream.
 */
public class CardRenderEngine {

    private final MutableLiveData<CardRenderEvent> events = new MutableLiveData<>();
    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();
    private volatile String activeRequestId;
    private volatile Future<?> activeTask;

    public LiveData<CardRenderEvent> getEvents() {
        return events;
    }

    public synchronized void startTextRender(String requestId, String text, MultimodalInputCoordinator coordinator) {
        cancelCurrent();
        activeRequestId = requestId;
        events.postValue(CardRenderEvent.loading(requestId));

        activeTask = parseExecutor.submit(() -> {
            if (coordinator == null) {
                events.postValue(CardRenderEvent.error(requestId, "解析器未初始化"));
                events.postValue(CardRenderEvent.completed(requestId));
                return;
            }

            coordinator.parseTextStreaming(text, new MultimodalInputCoordinator.ParseStreamCallback() {
                @Override
                public void onStreamingText(String partialText) {
                    if (!isRequestActive(requestId)) {
                        return;
                    }
                    events.postValue(CardRenderEvent.streaming(requestId, partialText));
                }

                @Override
                public void onPlanReady(com.example.philotes.data.model.ActionPlan plan) {
                    if (!isRequestActive(requestId)) {
                        return;
                    }
                    events.postValue(CardRenderEvent.cardReady(requestId, plan));
                }

                @Override
                public void onError(Exception error) {
                    if (!isRequestActive(requestId)) {
                        return;
                    }
                    String message = error == null ? "解析失败" : error.getMessage();
                    events.postValue(CardRenderEvent.error(requestId, message == null ? "解析失败" : message));
                }

                @Override
                public void onCompleted() {
                    if (!isRequestActive(requestId)) {
                        return;
                    }
                    events.postValue(CardRenderEvent.completed(requestId));
                }
            });
        });
    }

    public synchronized void cancelCurrent() {
        activeRequestId = null;
        if (activeTask != null) {
            activeTask.cancel(true);
            activeTask = null;
        }
    }

    public void release() {
        cancelCurrent();
        parseExecutor.shutdownNow();
    }

    private boolean isRequestActive(String requestId) {
        return requestId != null && requestId.equals(activeRequestId);
    }
}

