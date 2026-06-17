package com.plantify.chat.presentation.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantify.chat.application.port.in.StreamChatResponseUseCase;
import com.plantify.chat.domain.model.ChatMessage;
import com.plantify.chat.domain.model.MessageType;
import com.plantify.chat.domain.model.SenderType;
import com.plantify.chat.infrastructure.metrics.ChatMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatMessageMapper messageMapper = new ChatMessageMapper(objectMapper);
    private final WebSocketMessageFactory messageFactory = new WebSocketMessageFactory(messageMapper);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ChatMetrics chatMetrics = new ChatMetrics(meterRegistry);

    @Test
    void sendsAiChunksFromChatService() {
        StreamChatResponseUseCase streamChatResponseUseCase = mock(StreamChatResponseUseCase.class);
        when(streamChatResponseUseCase.streamResponse("hello"))
                .thenReturn(Flux.just("안녕하세요. ", "테스트 응답입니다."));

        TestWebSocketSession session = TestWebSocketSession.withIncoming(
                jsonMessage("USER", "hello", MessageType.CHAT)
        );
        ChatWebSocketHandler handler = new ChatWebSocketHandler(streamChatResponseUseCase, messageMapper, messageFactory, chatMetrics);

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        assertThat(session.sentPayloads()).hasSize(2);
        assertThat(read(session.sentPayloads().get(0))).satisfies(message -> {
            assertThat(message.getSender()).isEqualTo(SenderType.AI);
            assertThat(message.getMessage()).isEqualTo("안녕하세요. ");
            assertThat(message.getType()).isEqualTo(MessageType.CHAT);
        });
        assertThat(read(session.sentPayloads().get(1)).getMessage()).isEqualTo("테스트 응답입니다.");
        assertThat(counterValue("chat.messages.received")).isEqualTo(1.0);
        assertThat(gaugeValue("chat.websocket.sessions.active")).isZero();
    }

    @Test
    void invalidJsonReturnsSystemError() {
        StreamChatResponseUseCase streamChatResponseUseCase = mock(StreamChatResponseUseCase.class);
        TestWebSocketSession session = TestWebSocketSession.withIncoming("{ invalid");
        ChatWebSocketHandler handler = new ChatWebSocketHandler(streamChatResponseUseCase, messageMapper, messageFactory, chatMetrics);

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        assertThat(session.sentPayloads()).hasSize(1);
        assertThat(read(session.sentPayloads().get(0))).satisfies(message -> {
            assertThat(message.getSender()).isEqualTo(SenderType.SYSTEM);
            assertThat(message.getMessage()).isEqualTo("Invalid message format");
            assertThat(message.getType()).isEqualTo(MessageType.ERROR);
        });
        verifyNoInteractions(streamChatResponseUseCase);
        assertThat(counterValue("chat.messages.invalid")).isEqualTo(1.0);
    }

    @Test
    void chatServiceErrorReturnsSystemError() {
        StreamChatResponseUseCase streamChatResponseUseCase = mock(StreamChatResponseUseCase.class);
        when(streamChatResponseUseCase.streamResponse("hello"))
                .thenReturn(Flux.error(new IllegalStateException("AI down")));

        TestWebSocketSession session = TestWebSocketSession.withIncoming(
                jsonMessage("USER", "hello", MessageType.CHAT)
        );
        ChatWebSocketHandler handler = new ChatWebSocketHandler(streamChatResponseUseCase, messageMapper, messageFactory, chatMetrics);

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        assertThat(session.sentPayloads()).hasSize(1);
        assertThat(read(session.sentPayloads().get(0))).satisfies(message -> {
            assertThat(message.getSender()).isEqualTo(SenderType.SYSTEM);
            assertThat(message.getMessage()).isEqualTo("Error in AI service");
            assertThat(message.getType()).isEqualTo(MessageType.ERROR);
        });
    }

    @Test
    void preservesResponseOrderForConsecutiveMessagesInSameSession() {
        StreamChatResponseUseCase streamChatResponseUseCase = mock(StreamChatResponseUseCase.class);
        when(streamChatResponseUseCase.streamResponse("first"))
                .thenReturn(Flux.just("first-1", "first-2").delayElements(Duration.ofMillis(10)));
        when(streamChatResponseUseCase.streamResponse("second"))
                .thenReturn(Flux.just("second-1"));

        TestWebSocketSession session = TestWebSocketSession.withIncoming(
                jsonMessage("USER", "first", MessageType.CHAT),
                jsonMessage("USER", "second", MessageType.CHAT)
        );
        ChatWebSocketHandler handler = new ChatWebSocketHandler(streamChatResponseUseCase, messageMapper, messageFactory, chatMetrics);

        StepVerifier.create(handler.handle(session))
                .verifyComplete();

        assertThat(session.sentPayloads())
                .map(payload -> read(payload).getMessage())
                .containsExactly("first-1", "first-2", "second-1");
    }

    private String jsonMessage(String sender, String message, MessageType type) {
        try {
            return objectMapper.writeValueAsString(ChatMessage.builder()
                    .sender(SenderType.valueOf(sender))
                    .message(message)
                    .type(type)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ChatMessage read(String payload) {
        try {
            return objectMapper.readValue(payload, ChatMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private double counterValue(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private double gaugeValue(String name) {
        return meterRegistry.get(name).gauge().value();
    }

}
