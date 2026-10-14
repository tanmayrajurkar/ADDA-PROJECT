package com.adda.marketbot.llm;

import com.adda.marketbot.config.BotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final BotProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final int timeoutSeconds;

    public GeminiClient(BotProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.timeoutSeconds = Math.max(2, properties.getLlm().getTimeoutSeconds());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Optional<String> rewrite(String question, String groundedAnswer) {
        if (!properties.getLlm().isEnabled() || groundedAnswer.isBlank()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.putObject("systemInstruction")
                    .putArray("parts")
                    .addObject()
                    .put("text", """
                            You rewrite a grounded answer for an Indian stock market help desk.
                            Use ONLY the facts in the grounded answer. Do not add prices, forecasts, or outside facts.
                            Do not tell the user to buy, sell, or hold anything.
                            If the grounded answer does not address the question, return it unchanged.
                            Keep the reply under 140 words.
                            """);
            body.putArray("contents")
                    .addObject()
                    .put("role", "user")
                    .putArray("parts")
                    .addObject()
                    .put("text", "QUESTION:\n" + question + "\n\nGROUNDED ANSWER:\n" + groundedAnswer);
            ObjectNode generation = body.putObject("generationConfig");
            generation.put("temperature", 0.2);
            generation.put("maxOutputTokens", 512);

            String model = properties.getLlm().getModel();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.getLlm().getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            String text = readText(mapper.readTree(response.body()));
            if (text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (Exception ex) {
            log.warn("Gemini rewrite unavailable, using the grounded answer: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String readText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode value = part.path("text");
            if (!value.isTextual() || value.asText().isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(value.asText().trim());
        }
        return text.toString().trim();
    }
}
