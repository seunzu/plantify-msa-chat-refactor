import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

export const options = {
  scenarios: {
    websocket_chat_load: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    websocket_connection_success: ['rate>0.95'],
    websocket_stream_completed: ['rate>0.95'],
    websocket_first_token_latency: ['p(95)<1000'],
    websocket_stream_duration: ['p(95)<5000'],
  },
};

const chatWsUrl = __ENV.CHAT_WS_URL || 'ws://localhost:8080/chat';
const expectedChunks = Number(__ENV.EXPECTED_CHUNKS || 7);
const streamTimeoutMillis = Number(__ENV.STREAM_TIMEOUT_MILLIS || 5000);

const connectionSuccess = new Rate('websocket_connection_success');
const streamCompleted = new Rate('websocket_stream_completed');
const chunksReceived = new Counter('websocket_chunks_received');
const firstTokenLatency = new Trend('websocket_first_token_latency');
const streamDuration = new Trend('websocket_stream_duration');

export default function () {
  const payload = JSON.stringify({
    sender: 'USER',
    message: `k6 websocket message vu=${__VU} iter=${__ITER}`,
    type: 'CHAT',
  });

  let opened = false;
  let completed = false;
  let chunkCount = 0;
  let startedAt = 0;
  let firstChunkAt = 0;

  const response = ws.connect(chatWsUrl, null, (socket) => {
    socket.on('open', () => {
      opened = true;
      startedAt = Date.now();
      socket.send(payload);
    });

    socket.on('message', (data) => {
      const receivedAt = Date.now();
      const message = JSON.parse(data);

      if (message.type === 'ERROR') {
        socket.close();
        return;
      }

      if (message.sender !== 'AI') {
        return;
      }

      chunkCount += 1;
      chunksReceived.add(1);

      if (firstChunkAt === 0) {
        firstChunkAt = receivedAt;
        firstTokenLatency.add(firstChunkAt - startedAt);
      }

      if (chunkCount >= expectedChunks) {
        completed = true;
        streamDuration.add(receivedAt - startedAt);
        socket.close();
      }
    });

    socket.on('error', () => {
      socket.close();
    });

    socket.setTimeout(() => {
      socket.close();
    }, streamTimeoutMillis);
  });

  connectionSuccess.add(opened && response && response.status === 101);
  streamCompleted.add(completed);

  check(response, {
    'websocket upgraded': (res) => res && res.status === 101,
    'stream completed': () => completed,
    'received expected chunks': () => chunkCount >= expectedChunks,
  });

  sleep(1);
}

