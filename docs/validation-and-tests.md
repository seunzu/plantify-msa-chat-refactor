# 검증과 테스트

이 문서는 WebSocket + gRPC streaming 구조에서 확인해야 할 검증 관점, k6 시나리오, 저장소에 기록한 부하 수치와 해석을 정리

현재 결과는 실제 chatbot 추론 성능이 아니라 mock gRPC 서버로 지연 응답을 재현한 전송 경로 검증 결과. 따라서 first-token latency와 stream duration을 실제 LLM 응답 성능으로 해석하지 않음

## 환경

- WebSocket 서버: `chat-service`
- AI 대체 서버: `mock-chatbot-server`
- 부하 도구: k6 v0.53.0
- 실행 환경: MacBook, Docker Compose
- WebSocket endpoint: `ws://chat-service:8080/chat`
- gRPC endpoint: `mock-chatbot-server:50052`
- 응답 chunk 수: 메시지당 7개
- stream timeout: 5초

## 검증 관점

- WebSocket 연결이 부하 중에도 정상적으로 upgrade 되는가
- gRPC server streaming 응답 chunk가 WebSocket 메시지로 끝까지 전달되는가
- 첫 chunk가 전체 답변 완료 전 빠르게 전달되는가
- 부하 중 stream timeout, WebSocket error, gRPC error가 발생하지 않는가
- k6 수치를 실제 chatbot 추론 성능처럼 과장하지 않는가

## 시나리오: WebSocket streaming 부하

```bash
docker compose --profile test run --rm -e VUS=10 -e DURATION=30s k6 run /scripts/websocket-chat-load.js
```

각 VU는 다음 흐름을 반복

1. WebSocket `/chat` 연결
2. `USER` chat message 전송
3. AI 응답 chunk 수신
4. 7개 chunk를 모두 받으면 stream completed로 기록
5. WebSocket 연결 종료

### VU 10 결과

| 메트릭 | 값 |
| --- | ---: |
| 실행 시간 | 30초 |
| iterations | 180 |
| WebSocket sessions | 180 |
| response chunks | 1,260 |
| connection success | 100.00% |
| stream completed | 100.00% |
| first-token latency p95 | 55ms |
| stream duration p95 | 757ms |
| gRPC errors | 0 |

### VU 50 결과

| 메트릭 | 값 |
| --- | ---: |
| 실행 시간 | 20초 |
| iterations | 600 |
| WebSocket sessions | 600 |
| response chunks | 4,200 |
| connection success | 100.00% |
| stream completed | 100.00% |
| first-token latency p95 | 117.85ms |
| stream duration p95 | 776ms |
| gRPC errors | 0 |

`response chunks: 4,200`은 600개 session에서 각 7개 chunk를 받은 값

```text
600 sessions * 7 chunks = 4,200 chunks
```

## 해석

- mock 기반 부하 테스트에서 VU 50 / 20초까지 WebSocket 연결과 stream completion이 모두 성공
- 600개 session이 각 7개 chunk를 수신해 총 4,200개 response chunk가 전달됨
- first-token latency와 stream duration은 전송 경로 지표이며 실제 chatbot 추론 성능으로 해석하지 않음
- WebSocket 종료 시 gRPC stream cancellation 전파와 느린 클라이언트 상황은 추가 검증이 필요
