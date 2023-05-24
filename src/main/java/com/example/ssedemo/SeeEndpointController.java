package com.example.ssedemo;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author zhangbaowei
 * @description:
 * @date 2023/5/23 14:37
 */

@RestController
public class SeeEndpointController {
    private ExecutorService nonBlockingService = Executors.newCachedThreadPool();

    @GetMapping("/sse")
    public SseEmitter handleSse() {
        SseEmitter emitter = new SseEmitter(0L);

        nonBlockingService.execute(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    emitter.send("SSE MVC - " + i);
                    Thread.sleep(1000);
                }
                emitter.complete();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    //启动这里
    @GetMapping("/proxy/sse")
    public SseEmitter proxy() {
        SseEmitter emitter = new SseEmitter(0L);

        nonBlockingService.execute(() -> {
            try {
                sseClient(emitter);
                emitter.complete();
            } catch (Exception e) {
                e.printStackTrace();
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sseClient(SseEmitter emitter) {
        CountDownLatch latch = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("http://localhost:8080/sse") // replace with your SSE endpoint
                .build();

        EventSourceListener listener = new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                System.out.println("Connection opened!");
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                System.out.println("Event received: " + data);
                try {
                    emitter.send("proyx" + data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                latch.countDown();
                System.out.println("Connection closed!");
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                System.err.println("Error: " + t.getMessage());
                latch.countDown();
            }
        };

        EventSource source = EventSources.createFactory(client).newEventSource(request, listener);

        try {
            latch.await(); // Wait forever.
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        source.cancel(); // Close the connection when done
    }
}
