package com.plantify.mockchatbot;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class MockChatbotServerApplication {

    private static final int DEFAULT_PORT = 50052;

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);

        Server server = ServerBuilder.forPort(port)
                .addService(new MockChatService())
                .build()
                .start();

        System.out.printf("Mock chatbot gRPC server started on port %d%n", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Mock chatbot gRPC server shutting down");
            server.shutdown();
        }));

        server.awaitTermination();
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Integer.parseInt(args[0]);
        }

        String envPort = System.getenv("MOCK_CHATBOT_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }

        return DEFAULT_PORT;
    }
}
