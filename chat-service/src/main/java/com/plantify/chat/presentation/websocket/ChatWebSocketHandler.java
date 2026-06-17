package com.plantify.chat.presentation.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.plantify.chat.application.port.in.StreamChatResponseUseCase;
import com.plantify.chat.domain.model.SenderType;
import com.plantify.chat.infrastructure.metrics.ChatMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final StreamChatResponseUseCase streamChatResponseUseCase;
    private final ChatMessageMapper messageMapper;
    private final WebSocketMessageFactory messageFactory;
    private final ChatMetrics chatMetrics;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return Mono.defer(() -> {
            chatMetrics.webSocketSessionOpened();

            Flux<WebSocketMessage> incomingMessages = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(payload -> chatMetrics.messageReceived())
                    .concatMap(payload -> handleMessage(payload, session))
                    .onErrorResume(e -> handleWebSocketError(session, e));

            return session.send(incomingMessages)
                    .doFinally(signalType -> chatMetrics.webSocketSessionClosed());
        });
    }

    private Flux<WebSocketMessage> handleMessage(String payload, WebSocketSession session) {
        try {
            log.info("Received payload: {}", payload);
            var userMessage = messageMapper.fromJson(payload);

            return streamChatResponseUseCase.streamResponse(userMessage.getMessage())
                    .map(reply -> messageFactory.chat(session, SenderType.AI, reply))
                    .onErrorResume(e -> Flux.just(messageFactory.error(session, "Error in AI service")));

        } catch (JsonProcessingException e) {
            chatMetrics.invalidMessage();
            log.warn("Invalid message format: {}", e.getOriginalMessage());
            return Flux.just(messageFactory.error(session, "Invalid message format"));
        }
    }

    private Flux<WebSocketMessage> handleWebSocketError(WebSocketSession session, Throwable e) {
        log.error("WebSocket error: ", e);
        return Flux.just(messageFactory.error(session, "An error occurred: " + e.getMessage()));
    }
}
