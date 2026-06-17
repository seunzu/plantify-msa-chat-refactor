package com.plantify.chat.config;

import com.plantify.pb.unit.chat.ChatServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {

    @Value("${grpc.server.address}")
    private String grpcServerAddress;

    private ManagedChannel channel;

    @Bean
    public ManagedChannel managedChannel() {
        channel = ManagedChannelBuilder.forTarget(grpcServerAddress)
                .usePlaintext()
                .enableRetry()
                .maxRetryAttempts(3)
                .build();
        return channel;
    }

    @Bean
    public ChatServiceGrpc.ChatServiceStub chatServiceStub(ManagedChannel channel) {
        return ChatServiceGrpc.newStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
