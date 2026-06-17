package com.plantify.chat.infrastructure.grpc;

import com.plantify.chat.infrastructure.metrics.ChatMetrics;
import com.plantify.pb.unit.chat.ChatRequest;
import com.plantify.pb.unit.chat.ChatResponse;
import com.plantify.pb.unit.chat.ChatServiceGrpc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcAiChatStreamingAdapterTest {

    private Server server;
    private ManagedChannel channel;
    private SimpleMeterRegistry meterRegistry;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void emitsGrpcResponseChunksAndCompletes() throws Exception {
        RecordingChatGrpcService grpcService = new RecordingChatGrpcService(
                List.of("안녕하세요. ", "stream chunk입니다.")
        );
        GrpcAiChatStreamingAdapter chatService = chatServiceWith(grpcService);

        StepVerifier.create(chatService.streamResponse("hello"))
                .expectNext("안녕하세요. ")
                .expectNext("stream chunk입니다.")
                .verifyComplete();

        assertThat(grpcService.lastRequest.getMessage()).isEqualTo("hello");
        assertThat(grpcService.lastRequest.getSender()).isEqualTo("USER");
        assertThat(counterValue("chat.streams.started")).isEqualTo(1.0);
        assertThat(counterValue("chat.stream.chunks.sent")).isEqualTo(2.0);
        assertThat(counterValue("chat.streams.completed")).isEqualTo(1.0);
        assertThat(timerCount("chat.stream.duration")).isEqualTo(1);
        assertThat(timerCount("chat.first.token.latency")).isEqualTo(1);
    }

    @Test
    void propagatesGrpcErrorToFlux() throws Exception {
        GrpcAiChatStreamingAdapter chatService = chatServiceWith(new ErrorChatGrpcService());

        StepVerifier.create(chatService.streamResponse("hello"))
                .expectErrorMatches(error ->
                        error.getMessage() != null && error.getMessage().contains("mock failure")
                )
                .verify();
        assertThat(counterValue("chat.grpc.errors")).isEqualTo(1.0);
        assertThat(timerCount("chat.stream.duration")).isEqualTo(1);
    }

    private GrpcAiChatStreamingAdapter chatServiceWith(ChatServiceGrpc.ChatServiceImplBase grpcService) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(grpcService)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        meterRegistry = new SimpleMeterRegistry();
        return new GrpcAiChatStreamingAdapter(ChatServiceGrpc.newStub(channel), new ChatMetrics(meterRegistry));
    }

    private double counterValue(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private long timerCount(String name) {
        return meterRegistry.get(name).timer().count();
    }

    private static class RecordingChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

        private final List<String> chunks;
        private ChatRequest lastRequest;

        private RecordingChatGrpcService(List<String> chunks) {
            this.chunks = chunks;
        }

        @Override
        public void streamMessage(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
            this.lastRequest = request;
            chunks.stream()
                    .map(reply -> ChatResponse.newBuilder().setReply(reply).build())
                    .forEach(responseObserver::onNext);
            responseObserver.onCompleted();
        }
    }

    private static class ErrorChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

        @Override
        public void streamMessage(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("mock failure")
                    .asRuntimeException());
        }
    }
}
