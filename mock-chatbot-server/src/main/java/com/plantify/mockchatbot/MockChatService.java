package com.plantify.mockchatbot;

import com.plantify.pb.unit.chat.ChatRequest;
import com.plantify.pb.unit.chat.ChatResponse;
import com.plantify.pb.unit.chat.ChatServiceGrpc;
import com.plantify.pb.unit.common.Status;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MockChatService extends ChatServiceGrpc.ChatServiceImplBase {

    private static final long TOKEN_DELAY_MILLIS = 120L;

    @Override
    public void streamMessage(ChatRequest request, StreamObserver<ChatResponse> responseObserver) {
        List<String> chunks = List.of(
                "안녕하세요. ",
                "저는 테스트용 AI 응답을 ",
                "gRPC streaming으로 ",
                "나눠 보내는 mock 서버입니다. ",
                "방금 보낸 메시지는 \"",
                request.getMessage(),
                "\" 입니다."
        );

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger index = new AtomicInteger();

        scheduler.scheduleAtFixedRate(() -> {
            int current = index.getAndIncrement();

            if (current >= chunks.size()) {
                responseObserver.onCompleted();
                scheduler.shutdown();
                return;
            }

            ChatResponse response = ChatResponse.newBuilder()
                    .setReply(chunks.get(current))
                    .setStatus(Status.newBuilder()
                            .setCode(200)
                            .setMessage("OK")
                            .build())
                    .build();

            responseObserver.onNext(response);
        }, 0, TOKEN_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }
}
