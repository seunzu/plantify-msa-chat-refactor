package com.plantify.chat.presentation.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantify.chat.domain.model.ChatMessage;
import com.plantify.chat.domain.model.MessageType;
import com.plantify.chat.domain.model.SenderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketMessageFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketMessageFactory factory = new WebSocketMessageFactory(
            new ChatMessageMapper(objectMapper)
    );

    @Test
    void createsChatWebSocketMessage() throws Exception {
        TestWebSocketSession session = TestWebSocketSession.withIncoming();

        String payload = factory.chat(session, SenderType.AI, "hello").getPayloadAsText();
        ChatMessage message = objectMapper.readValue(payload, ChatMessage.class);

        assertThat(message.getSender()).isEqualTo(SenderType.AI);
        assertThat(message.getMessage()).isEqualTo("hello");
        assertThat(message.getType()).isEqualTo(MessageType.CHAT);
    }

    @Test
    void createsErrorWebSocketMessage() throws Exception {
        TestWebSocketSession session = TestWebSocketSession.withIncoming();

        String payload = factory.error(session, "Invalid message").getPayloadAsText();
        ChatMessage message = objectMapper.readValue(payload, ChatMessage.class);

        assertThat(message.getSender()).isEqualTo(SenderType.SYSTEM);
        assertThat(message.getMessage()).isEqualTo("Invalid message");
        assertThat(message.getType()).isEqualTo(MessageType.ERROR);
    }
}
