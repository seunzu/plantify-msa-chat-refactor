package com.plantify.chat.presentation.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantify.chat.domain.model.ChatMessage;
import com.plantify.chat.domain.model.MessageType;
import com.plantify.chat.domain.model.SenderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageMapperTest {

    private final ChatMessageMapper mapper = new ChatMessageMapper(new ObjectMapper());

    @Test
    void deserializeChatMessage() throws Exception {
        String payload = """
                {
                  "sender": "USER",
                  "message": "hello",
                  "type": "CHAT"
                }
                """;

        ChatMessage message = mapper.fromJson(payload);

        assertThat(message.getSender()).isEqualTo(SenderType.USER);
        assertThat(message.getMessage()).isEqualTo("hello");
        assertThat(message.getType()).isEqualTo(MessageType.CHAT);
    }

    @Test
    void serializeChatMessage() throws Exception {
        ChatMessage message = ChatMessage.chat(SenderType.AI, "stream chunk");

        String payload = mapper.toJson(message);

        assertThat(payload).contains("\"sender\":\"AI\"");
        assertThat(payload).contains("\"message\":\"stream chunk\"");
        assertThat(payload).contains("\"type\":\"CHAT\"");
    }

    @Test
    void invalidJsonFails() {
        assertThatThrownBy(() -> mapper.fromJson("{ invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
