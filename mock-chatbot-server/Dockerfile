FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/build/install/mock-chatbot-server/ ./
EXPOSE 50052
ENTRYPOINT ["./bin/mock-chatbot-server"]
