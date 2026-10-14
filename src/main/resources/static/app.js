const thread = document.querySelector("#thread");
const form = document.querySelector("#composer");
const messageBox = document.querySelector("#message");
const sendButton = document.querySelector("#send");
const topics = document.querySelector("#topics");
const prompts = document.querySelector("#prompts");
const clearButton = document.querySelector("#clear");
const passageCount = document.querySelector("#passage-count");

const sessionKey = "adda-market-session";
let sessionId = sessionStorage.getItem(sessionKey) || "";
let pending = false;

messageBox.addEventListener("input", () => {
    messageBox.style.height = "auto";
    messageBox.style.height = Math.min(messageBox.scrollHeight, 140) + "px";
});

messageBox.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
    }
});

form.addEventListener("submit", (event) => {
    event.preventDefault();
    ask(messageBox.value);
});

prompts.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-ask]");
    if (button) {
        ask(button.dataset.ask);
    }
});

topics.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-title]");
    if (!button) {
        return;
    }
    const title = button.dataset.title;
    const question = /^(how|what|why|when|where)\b/i.test(title) ? title : `Explain ${title}.`;
    ask(question);
});

clearButton.addEventListener("click", async () => {
    if (sessionId) {
        await fetch(`/api/sessions/${encodeURIComponent(sessionId)}`, { method: "DELETE" });
    }
    sessionId = "";
    sessionStorage.removeItem(sessionKey);
    thread.replaceChildren();
    addBubble("Conversation cleared. Ask a new question from the knowledge base.", "assistant", false, []);
});

loadDesk();

async function loadDesk() {
    try {
        const [health, topicList] = await Promise.all([
            fetch("/api/health").then((response) => response.json()),
            fetch("/api/topics").then((response) => response.json())
        ]);
        passageCount.textContent = `${health.chunks} passages in the knowledge base`;
        topics.replaceChildren();
        for (const topic of topicList) {
            const button = document.createElement("button");
            button.type = "button";
            button.dataset.title = topic.title;
            const name = document.createElement("strong");
            name.textContent = topic.title;
            const summary = document.createElement("span");
            summary.textContent = topic.summary;
            button.append(name, summary);
            topics.append(button);
        }
    } catch (error) {
        passageCount.textContent = "Knowledge base unavailable";
    }
}

async function ask(raw) {
    const message = raw.trim();
    if (!message || pending) {
        return;
    }
    pending = true;
    sendButton.disabled = true;
    addBubble(message, "user", false, []);
    messageBox.value = "";
    messageBox.style.height = "auto";
    const waiting = addBubble("Checking the knowledge base…", "assistant", false, []);

    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ sessionId, message })
        });
        const body = await response.json();
        if (!response.ok) {
            waiting.remove();
            addBubble(body.error || "The desk could not answer just now.", "assistant", true, []);
            return;
        }
        sessionId = body.sessionId;
        sessionStorage.setItem(sessionKey, sessionId);
        waiting.remove();
        addBubble(body.answer, "assistant", body.refused, body.sources || [], body.mode);
    } catch (error) {
        waiting.remove();
        addBubble("The desk could not be reached. If you are using Docker, confirm the app is up.", "assistant", true, []);
    } finally {
        pending = false;
        sendButton.disabled = false;
        messageBox.focus();
    }
}

function addBubble(text, role, refused, sources, mode) {
    const article = document.createElement("article");
    article.className = `bubble ${role}` + (refused ? " refused" : "");
    if (role === "assistant") {
        const meta = document.createElement("div");
        meta.className = "meta";
        meta.textContent = refused ? "Outside the knowledge base" : (mode === "gemini" ? "From context, phrased by Gemini" : "From the knowledge base");
        article.append(meta);
    }
    const paragraph = document.createElement("p");
    paragraph.textContent = text;
    article.append(paragraph);
    if (sources && sources.length) {
        const list = document.createElement("div");
        list.className = "sources";
        for (const source of sources) {
            const item = document.createElement("div");
            item.className = "source";
            const title = document.createElement("strong");
            title.textContent = `${source.title} · ${source.document}`;
            const excerpt = document.createElement("div");
            excerpt.textContent = source.excerpt;
            item.append(title, excerpt);
            list.append(item);
        }
        article.append(list);
    }
    thread.append(article);
    thread.scrollTop = thread.scrollHeight;
    return article;
}
