package com.plantify.chat.application.port.in;

import reactor.core.publisher.Flux;

public interface StreamChatResponseUseCase {

    Flux<String> streamResponse(String userMessage);
}
