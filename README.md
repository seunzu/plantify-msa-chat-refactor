# plantify-msa-chat-refactor

Spring Boot MSA 환경에서 AI 채팅 응답 지연을 다루기 위한 로컬 실험 레포

- Baseline: WebSocket 서버가 AI 응답 완료까지 대기한 뒤 클라이언트에 전달
- Refactor target: WebSocket 서버가 AI 응답을 stream chunk 단위로 즉시 전달

## 핵심 변경

- Spring MVC 기반 처리 흐름을 WebFlux 기반 non-blocking 흐름으로 전환
- HTTP(S) 요청/응답 방식의 AI 호출을 gRPC server streaming으로 전환
- AI 응답 chunk를 Reactor `Flux`로 변환해 WebSocket으로 즉시 전달
- 동일 WebSocket 세션에서 연속 메시지 응답 순서 보장

## 서비스

| 서비스 | 역할 |
| --- | --- |
| `chat-service` | WebFlux WebSocket 서버, gRPC streaming 응답을 WebSocket chunk로 전달 |
| `mock-chatbot-server` | 실제 AI 서버를 대체하는 테스트용 gRPC streaming 서버 |
| `experiments/k6` | WebSocket streaming 부하 테스트 스크립트 |

## 로컬 실행

```bash
docker compose up --build mock-chatbot-server chat-service
```

브라우저 접속:

```text
http://localhost:8080
```

## 실험 실행

WebSocket streaming 부하 테스트:

```bash
docker compose --profile test run --rm -e VUS=10 -e DURATION=30s k6 run /scripts/websocket-chat-load.js
```

## 문서

- [Architecture](docs/architecture.md)
- [Validation and Tests](docs/validation-and-tests.md)
