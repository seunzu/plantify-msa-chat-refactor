let socket = null;
let currentAiBubble = null;
let responseStartedAt = null;

const conversation = document.getElementById("conversation");
const statusText = document.getElementById("connectionStatus");
const chatForm = document.getElementById("chatForm");
const messageInput = document.getElementById("message");

window.addEventListener("load", connect);
chatForm.addEventListener("submit", sendMessage);

function connect() {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${protocol}://${window.location.host}/chat`);

    socket.onopen = function () {
        setStatus("연결됨");
        messageInput.disabled = false;
    };

    socket.onmessage = function (event) {
        const messageData = JSON.parse(event.data);

        if (messageData.type === "ERROR") {
            currentAiBubble = null;
            appendBubble("SYSTEM", messageData.message, "system");
            return;
        }

        if (messageData.sender === "AI") {
            appendAiChunk(messageData.message);
            return;
        }

        appendBubble(messageData.sender, messageData.message, "received");
    };

    socket.onclose = function () {
        setStatus("연결 끊김. 재연결 중");
        messageInput.disabled = true;
        currentAiBubble = null;
        setTimeout(connect, 1500);
    };

    socket.onerror = function () {
        setStatus("연결 오류");
    };
}

function sendMessage(event) {
    event.preventDefault();

    const messageContent = messageInput.value.trim();
    if (!messageContent) {
        return;
    }

    if (!socket || socket.readyState !== WebSocket.OPEN) {
        appendBubble("SYSTEM", "WebSocket 연결이 아직 준비되지 않았습니다.", "system");
        return;
    }

    appendBubble("USER", messageContent, "sent");
    currentAiBubble = createTypingBubble();
    responseStartedAt = performance.now();

    socket.send(JSON.stringify({
        sender: "USER",
        message: messageContent,
        type: "CHAT"
    }));

    messageInput.value = "";
    messageInput.focus();
}

function appendAiChunk(chunk) {
    if (!currentAiBubble) {
        currentAiBubble = createTypingBubble();
        responseStartedAt = performance.now();
    }

    const content = currentAiBubble.querySelector(".message-content");
    const meta = currentAiBubble.querySelector(".message-meta");
    const text = content.dataset.text || "";
    const nextText = text + chunk;

    content.dataset.text = nextText;
    content.textContent = nextText;

    if (responseStartedAt) {
        const elapsed = Math.round(performance.now() - responseStartedAt);
        meta.textContent = `${elapsed}ms`;
    }

    scrollToBottom();
}

function createTypingBubble() {
    return appendBubble("AI", "", "received", "streaming");
}

function appendBubble(sender, message, direction, state) {
    const row = document.createElement("div");
    row.className = `message-row ${direction}`;

    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    if (state) {
        bubble.classList.add(state);
    }

    const label = document.createElement("div");
    label.className = "message-label";
    label.textContent = sender;

    const content = document.createElement("div");
    content.className = "message-content";
    content.textContent = message;
    content.dataset.text = message;

    const meta = document.createElement("div");
    meta.className = "message-meta";
    meta.textContent = new Date().toLocaleTimeString("ko-KR", {
        hour: "2-digit",
        minute: "2-digit"
    });

    bubble.appendChild(label);
    bubble.appendChild(content);
    bubble.appendChild(meta);
    row.appendChild(bubble);
    conversation.appendChild(row);
    scrollToBottom();

    return row;
}

function setStatus(message) {
    statusText.textContent = message;
}

function scrollToBottom() {
    conversation.scrollTop = conversation.scrollHeight;
}
