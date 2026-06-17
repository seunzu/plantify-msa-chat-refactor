package com.plantify.chat.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private SenderType sender;
    private String message;
    private MessageType type;

    public static ChatMessage chat(SenderType sender, String message) {
        return ChatMessage.builder()
                .sender(sender)
                .message(message)
                .type(MessageType.CHAT)
                .build();
    }

    public static ChatMessage error(String message) {
        return ChatMessage.builder()
                .sender(SenderType.SYSTEM)
                .message(message)
                .type(MessageType.ERROR)
                .build();
    }
}
