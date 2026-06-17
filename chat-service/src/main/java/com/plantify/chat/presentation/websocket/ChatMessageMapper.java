package com.plantify.chat.presentation.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantify.chat.domain.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageMapper {

    private final ObjectMapper objectMapper;

    public ChatMessage fromJson(String payload) throws JsonProcessingException {
        return objectMapper.readValue(payload, ChatMessage.class);
    }

    public String toJson(ChatMessage message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message);
    }
}
