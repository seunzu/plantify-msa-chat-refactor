package com.plantify.chat.presentation.websocket;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class TestWebSocketSession implements WebSocketSession {

    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final Flux<WebSocketMessage> incoming;
    private final List<String> sentPayloads = new ArrayList<>();

    private TestWebSocketSession(Flux<WebSocketMessage> incoming) {
        this.incoming = incoming;
    }

    static TestWebSocketSession withIncoming(String... payloads) {
        return new TestWebSocketSession(Flux.fromArray(payloads)
                .map(payload -> new WebSocketMessage(
                        WebSocketMessage.Type.TEXT,
                        BUFFER_FACTORY.wrap(payload.getBytes(StandardCharsets.UTF_8))
                )));
    }

    List<String> sentPayloads() {
        return sentPayloads;
    }

    @Override
    public String getId() {
        return "test-session";
    }

    @Override
    public HandshakeInfo getHandshakeInfo() {
        return new HandshakeInfo(URI.create("ws://localhost/chat"), HttpHeaders.EMPTY, Mono.empty(), null);
    }

    @Override
    public DataBufferFactory bufferFactory() {
        return BUFFER_FACTORY;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return new HashMap<>();
    }

    @Override
    public Flux<WebSocketMessage> receive() {
        return incoming;
    }

    @Override
    public Mono<Void> send(Publisher<WebSocketMessage> messages) {
        return Flux.from(messages)
                .doOnNext(message -> sentPayloads.add(message.getPayloadAsText()))
                .then();
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public Mono<Void> close(CloseStatus status) {
        return Mono.empty();
    }

    @Override
    public Mono<CloseStatus> closeStatus() {
        return Mono.empty();
    }

    @Override
    public WebSocketMessage textMessage(String payload) {
        return new WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                BUFFER_FACTORY.wrap(payload.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Override
    public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
        return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(BUFFER_FACTORY));
    }

    @Override
    public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
        return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(BUFFER_FACTORY));
    }

    @Override
    public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> payloadFactory) {
        return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(BUFFER_FACTORY));
    }
}
