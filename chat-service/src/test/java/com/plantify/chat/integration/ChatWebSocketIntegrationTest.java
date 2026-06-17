package com.plantify.chat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantify.chat.domain.model.ChatMessage;
import com.plantify.chat.domain.model.MessageType;
import com.plantify.chat.domain.model.SenderType;
import com.plantify.pb.unit.chat.ChatRequest;
import com.plantify.pb.unit.chat.ChatResponse;
import com.plantify.pb.unit.chat.ChatServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {

    private static final int GRPC_TEST_PORT = findAvailablePort();

    private static Server grpcServer;

    @LocalServerPort
    private int webSocketPort;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("grpc.server.address", () -> "localhost:" + GRPC_TEST_PORT);
    }

    @BeforeAll
    static void startGrpcServer() throws IOException {
        grpcServer = ServerBuilder.forPort(GRPC_TEST_PORT)
                .addService(new TestStreamingChatService())
                .build()
                .start();
    }

    @AfterAll
    static void stopGrpcServer() {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    void websocketMessageReturnsGrpcStreamingChunks() throws Exception {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        String requestJson = objectMapper.writeValueAsString(ChatMessage.chat(SenderType.USER, "hello"));
        AtomicReference<List<ChatMessage>> receivedMessages = new AtomicReference<>();

        Mono<Void> result = client.execute(
                URI.create("ws://localhost:" + webSocketPort + "/chat"),
                session -> session.send(Mono.just(session.textMessage(requestJson)))
                        .thenMany(session.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .map(this::readMessage)
                                .take(3))
                        .collectList()
                        .doOnNext(receivedMessages::set)
                        .then()
        );

        StepVerifier.create(result)
                .verifyComplete();

        List<ChatMessage> messages = receivedMessages.get();
        assertThat(messages).hasSize(3);
        assertThat(messages).extracting(ChatMessage::getSender)
                .containsOnly(SenderType.AI);
        assertThat(messages).extracting(ChatMessage::getType)
                .containsOnly(MessageType.CHAT);
        assertThat(messages).extracting(ChatMessage::getMessage)
                .containsExactly("안녕하세요. ", "gRPC 스트리밍 ", "통합 테스트입니다.");
        assertThat(TestStreamingChatService.lastRequestMessage).isEqualTo("hello");
        assertThat(TestStreamingChatService.lastRequestSender).isEqualTo("USER");
    }

    @Test
    void invalidPayloadReturnsWebSocketErrorMessage() {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        AtomicReference<ChatMessage> receivedMessage = new AtomicReference<>();

        Mono<Void> result = client.execute(
                URI.create("ws://localhost:" + webSocketPort + "/chat"),
                session -> session.send(Mono.just(session.textMessage("{ invalid")))
                        .thenMany(session.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .map(this::readMessage)
                                .take(1))
                        .single()
                        .doOnNext(receivedMessage::set)
                        .then()
        );

        StepVerifier.create(result)
                .verifyComplete();

        ChatMessage message = receivedMessage.get();
        assertThat(message.getSender()).isEqualTo(SenderType.SYSTEM);
        assertThat(message.getMessage()).isEqualTo("Invalid message format");
        assertThat(message.getType()).isEqualTo(MessageType.ERROR);
    }

    private ChatMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class TestStreamingChatService extends ChatServiceGrpc.ChatServiceImplBase {

        private static volatile String lastRequestMessage;
        private static volatile String lastRequestSender;

        @Override
        public void streamMessage(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
            lastRequestMessage = request.getMessage();
            lastRequestSender = request.getSender();

            List<String> replies = List.of("안녕하세요. ", "gRPC 스트리밍 ", "통합 테스트입니다.");
            replies.stream()
                    .map(reply -> ChatResponse.newBuilder().setReply(reply).build())
                    .forEach(responseObserver::onNext);
            responseObserver.onCompleted();
        }
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to find available port", e);
        }
    }
}
