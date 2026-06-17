package com.plantify.chat.application.service;

import com.plantify.chat.application.port.in.StreamChatResponseUseCase;
import com.plantify.chat.application.port.out.AiChatStreamingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatStreamService implements StreamChatResponseUseCase {

    private final AiChatStreamingPort aiChatStreamingPort;

    @Override
    public Flux<String> streamResponse(String userMessage) {
        return aiChatStreamingPort.streamResponse(userMessage);
    }
}
