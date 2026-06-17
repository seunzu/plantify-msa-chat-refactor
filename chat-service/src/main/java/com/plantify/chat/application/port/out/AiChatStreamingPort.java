package com.plantify.chat.application.port.out;

import reactor.core.publisher.Flux;

public interface AiChatStreamingPort {

    Flux<String> streamResponse(String userMessage);
}
