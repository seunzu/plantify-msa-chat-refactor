package com.plantify.chat.infrastructure.grpc;

import com.plantify.chat.application.port.out.AiChatStreamingPort;
import com.plantify.chat.domain.model.SenderType;
import com.plantify.chat.infrastructure.metrics.ChatMetrics;
import com.plantify.pb.unit.chat.ChatRequest;
import com.plantify.pb.unit.chat.ChatResponse;
import com.plantify.pb.unit.chat.ChatServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcAiChatStreamingAdapter implements AiChatStreamingPort {

    private final ChatServiceGrpc.ChatServiceStub chatServiceStub;
    private final ChatMetrics chatMetrics;

    @Override
    public Flux<String> streamResponse(String userMessage) {
        ChatRequest request = ChatRequest.newBuilder()
                .setMessage(userMessage)
                .setSender(SenderType.USER.name())
                .build();

        log.info("gRPC request: {}", userMessage);

        return Flux.create(sink -> {
            chatMetrics.streamStarted();
            long startedAt = System.nanoTime();
            AtomicBoolean firstTokenRecorded = new AtomicBoolean(false);
            AtomicBoolean finished = new AtomicBoolean(false);

            sink.onCancel(() -> {
                if (finished.compareAndSet(false, true)) {
                    chatMetrics.streamCancelled();
                    recordStreamDuration(startedAt);
                }
            });

            StreamObserver<ChatResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(ChatResponse response) {
                    if (!sink.isCancelled()) {
                        if (firstTokenRecorded.compareAndSet(false, true)) {
                            chatMetrics.recordFirstTokenLatency(elapsedSince(startedAt));
                        }
                        chatMetrics.streamChunkSent();
                        log.info("gRPC response: {}", response);
                        sink.next(response.getReply());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (!sink.isCancelled() && finished.compareAndSet(false, true)) {
                        chatMetrics.grpcError();
                        recordStreamDuration(startedAt);
                        log.error("gRPC on error: {}", t.getMessage());
                        sink.error(t);
                    }
                }

                @Override
                public void onCompleted() {
                    if (!sink.isCancelled() && finished.compareAndSet(false, true)) {
                        chatMetrics.streamCompleted();
                        recordStreamDuration(startedAt);
                        log.info("gRPC onCompleted");
                        sink.complete();
                    }
                }
            };

            try {
                chatServiceStub.streamMessage(request, responseObserver);
                log.info("gRPC request sent");
            } catch (Exception e) {
                if (finished.compareAndSet(false, true)) {
                    chatMetrics.grpcError();
                    recordStreamDuration(startedAt);
                    log.error("Error sending gRPC request", e);
                    sink.error(e);
                }
            }
        }, FluxSink.OverflowStrategy.ERROR);
    }

    private void recordStreamDuration(long startedAt) {
        chatMetrics.recordStreamDuration(elapsedSince(startedAt));
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
