package com.plantify.chat.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChatMetrics {

    private final AtomicInteger activeWebSocketSessions = new AtomicInteger();
    private final Counter messagesReceived;
    private final Counter invalidMessages;
    private final Counter streamsStarted;
    private final Counter streamChunksSent;
    private final Counter streamsCompleted;
    private final Counter streamsCancelled;
    private final Counter grpcErrors;
    private final Timer streamDuration;
    private final Timer firstTokenLatency;

    public ChatMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("chat.websocket.sessions.active", activeWebSocketSessions, AtomicInteger::get)
                .description("Current number of active chat WebSocket sessions")
                .register(meterRegistry);

        this.messagesReceived = Counter.builder("chat.messages.received")
                .description("Total number of WebSocket chat messages received")
                .register(meterRegistry);
        this.invalidMessages = Counter.builder("chat.messages.invalid")
                .description("Total number of invalid WebSocket chat messages")
                .register(meterRegistry);
        this.streamsStarted = Counter.builder("chat.streams.started")
                .description("Total number of AI response streams started")
                .register(meterRegistry);
        this.streamChunksSent = Counter.builder("chat.stream.chunks.sent")
                .description("Total number of AI response chunks sent to the WebSocket flow")
                .register(meterRegistry);
        this.streamsCompleted = Counter.builder("chat.streams.completed")
                .description("Total number of AI response streams completed successfully")
                .register(meterRegistry);
        this.streamsCancelled = Counter.builder("chat.streams.cancelled")
                .description("Total number of AI response streams cancelled before completion")
                .register(meterRegistry);
        this.grpcErrors = Counter.builder("chat.grpc.errors")
                .description("Total number of gRPC streaming errors")
                .register(meterRegistry);
        this.streamDuration = Timer.builder("chat.stream.duration")
                .description("AI response stream duration")
                .register(meterRegistry);
        this.firstTokenLatency = Timer.builder("chat.first.token.latency")
                .description("Time from AI stream subscription to first response chunk")
                .register(meterRegistry);
    }

    public void webSocketSessionOpened() {
        activeWebSocketSessions.incrementAndGet();
    }

    public void webSocketSessionClosed() {
        activeWebSocketSessions.updateAndGet(count -> Math.max(0, count - 1));
    }

    public void messageReceived() {
        messagesReceived.increment();
    }

    public void invalidMessage() {
        invalidMessages.increment();
    }

    public void streamStarted() {
        streamsStarted.increment();
    }

    public void streamChunkSent() {
        streamChunksSent.increment();
    }

    public void streamCompleted() {
        streamsCompleted.increment();
    }

    public void streamCancelled() {
        streamsCancelled.increment();
    }

    public void grpcError() {
        grpcErrors.increment();
    }

    public void recordStreamDuration(Duration duration) {
        streamDuration.record(duration);
    }

    public void recordFirstTokenLatency(Duration duration) {
        firstTokenLatency.record(duration);
    }
}
