# ADDA India Market Desk

A context-only chatbot for the Indian stock market. Every answer is retrieved from the knowledge base packaged with the app. If a question is not covered, the desk says so instead of guessing. Conversation history is used only to resolve follow-ups such as "does it move if the next day is closed?"

The app is a Spring Boot service built with Maven and run with Docker.

## Run with Docker

Run every command below from the project folder, in this order.

### 1. Build

This compiles the app with Maven, runs the tests, and creates the image.

```bash
docker compose build
```

Build again only after the code changes. Add `--build` later in the start command if you prefer to build and start together: `docker compose up --build -d`.

### 2. Start

```bash
docker compose up -d
```

`-d` runs it in the background so you can keep using the terminal. Without `-d`, the logs stay on screen until you press Ctrl+C, which stops the app.

### 3. View

Open http://localhost:8080

Check that the container is running:

```bash
docker compose ps
```

Watch the logs:

```bash
docker compose logs -f
```

Leave the logs with Ctrl+C. That does not stop the app when it was started with `-d`.

### 4. Stop

This stops the app and leaves the image in place, so the next start is quick.

```bash
docker compose stop
```

Start it again with:

```bash
docker compose up -d
```

### 5. Down

This stops the app and removes the container. The image is still on your machine.

```bash
docker compose down
```

To run it after `down`, start from step 2. Use step 1 first only if the code changed.

## Run with Maven

Java 21 and Maven 3.9 are required.

```bash
mvn test
mvn spring-boot:run
```

## What "context" means

1. Documents in `src/main/resources/knowledge` are split into passages.
2. A question is matched to those passages with TF-IDF. Matches below the cutoff are refused.
3. The reply is assembled only from the matched sentences, and the UI shows those passages.
4. A short follow-up is expanded with the previous question so "it" still points at the right passage.
5. Live prices, foreign markets, and buy/sell tips are refused from the scope passages. The base has no live data and no recommendations.

Gemini rephrases an answer that was already retrieved from the knowledge base. It is not given permission to add facts. Put your key in a `.env` file next to `docker-compose.yml` (that file is gitignored):

```bash
GEMINI_API_KEY=your_key_here
```

Then repeat step 2 so the container picks up the key: `docker compose up -d`. If the key is missing or Gemini is unavailable, the desk still answers from the retrieved passages. `GEMINI_MODEL` defaults to `gemini-2.5-flash`.

## API

`POST /api/chat`

```bash
curl -s http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"What is the Nifty 50?"}'
```

Send the returned `sessionId` on the next turn. `GET /api/health` and `GET /api/topics` describe the running base. `DELETE /api/sessions/{id}` clears a conversation.

This desk is educational. It is not SEBI, not a broker, and not tax or investment advice. Tax figures in the knowledge base are dated to the rules described there and can change.
