package com.plantify.chat.presentation.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.plantify.chat.domain.model.ChatMessage;
import com.plantify.chat.domain.model.SenderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class WebSocketMessageFactory {

    private final ChatMessageMapper messageMapper;

    public WebSocketMessage chat(WebSocketSession session, SenderType sender, String message) {
        return create(session, ChatMessage.chat(sender, message));
    }

    public WebSocketMessage error(WebSocketSession session, String message) {
        return create(session, ChatMessage.error(message));
    }

    private WebSocketMessage create(WebSocketSession session, ChatMessage message) {
        try {
            return session.textMessage(messageMapper.toJson(message));
        } catch (JsonProcessingException e) {
            return session.textMessage(
                    "{\"sender\":\"SYSTEM\",\"message\":\"Critical error\",\"type\":\"ERROR\"}"
            );
        }
    }
}
