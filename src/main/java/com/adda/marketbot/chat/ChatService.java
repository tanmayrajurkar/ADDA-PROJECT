package com.adda.marketbot.chat;

import com.adda.marketbot.config.BotProperties;
import com.adda.marketbot.knowledge.Chunk;
import com.adda.marketbot.knowledge.KnowledgeLibrary;
import com.adda.marketbot.knowledge.Topic;
import com.adda.marketbot.llm.GeminiClient;
import com.adda.marketbot.retrieval.ScoredChunk;
import com.adda.marketbot.retrieval.TfidfIndex;
import com.adda.marketbot.retrieval.Tokenizer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ChatService {

    static final String REFUSAL = "I don't have that in the Indian stock market knowledge base, so I can't answer it from context.";

    private static final Pattern GREETING = Pattern.compile(
            "(?i)^(hi|hello|hey|good morning|good evening|good afternoon|namaste|help)[!.\\s]*$");
    private static final Pattern ADVICE = Pattern.compile(
            "(?i)(should i|shall i|buy or sell|do you recommend|stock tips?|multibagger|target price|guaranteed return|best stock to|which stock to buy|can i buy)");
    private static final Pattern LIVE = Pattern.compile(
            "(?i)(\\bltp\\b|\\bcmp\\b|share price|stock price|live price|current price|trading at|right now|today'?s (close|level|price)|nifty today|sensex today|market today)");
    private static final Pattern FOLLOW_UP = Pattern.compile(
            "(?i)^(?:and|also|what about|how about)\\b|\\b(it|that|this|they|those|them|its|their|the same)\\b");
    private static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final KnowledgeLibrary library;
    private final TfidfIndex index;
    private final BotProperties properties;
    private final GeminiClient gemini;
    private final SessionStore sessions = new SessionStore();

    public ChatService(KnowledgeLibrary library, TfidfIndex index, BotProperties properties, GeminiClient gemini) {
        this.library = library;
        this.index = index;
        this.properties = properties;
        this.gemini = gemini;
    }

    @PostConstruct
    void requirePolicySections() {
        library.requireHeading("How to ask");
        library.requireHeading("No investment advice");
        library.requireHeading("What this assistant cannot answer");
    }

    public List<Topic> topics() {
        return library.topics();
    }

    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("chunks", index.size());
        body.put("llm", properties.getLlm().isEnabled());
        return body;
    }

    public void clear(String sessionId) {
        if (sessionId != null && SESSION_ID.matcher(sessionId).matches()) {
            sessions.clear(sessionId);
        }
    }

    public ChatResponse chat(String sessionId, String message) {
        String clean = message.trim().replaceAll("\\s+", " ");
        String id = sessionId != null && SESSION_ID.matcher(sessionId).matches()
                ? sessionId
                : UUID.randomUUID().toString();
        List<SessionStore.Turn> history = sessions.history(id);

        if (GREETING.matcher(clean).matches()) {
            return replyFromHeading(id, clean, "How to ask", false);
        }
        if (ADVICE.matcher(clean).find()) {
            return replyFromHeading(id, clean, "No investment advice", false);
        }
        if (LIVE.matcher(clean).find()) {
            return replyFromHeading(id, clean, "What this assistant cannot answer", true);
        }
        if (lacksContextTerms(clean)) {
            return remember(id, clean, REFUSAL, true, "retrieval", List.of());
        }

        String query = expand(clean, history);
        int topK = Math.max(1, properties.getTopK());
        List<ScoredChunk> hits = index.search(query, topK);
        if (hits.isEmpty() || hits.get(0).score() < properties.getMinScore()) {
            return remember(id, clean, REFUSAL, true, "retrieval", List.of());
        }

        double best = hits.get(0).score();
        double cutoff = Math.max(properties.getMinScore(), best * 0.94);
        List<ScoredChunk> strong = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            if (hit.score() >= cutoff) {
                strong.add(hit);
            }
        }

        GroundedAnswerer.GroundedAnswer grounded = GroundedAnswerer.compose(clean, strong);
        if (grounded.text().isBlank()) {
            return remember(id, clean, REFUSAL, true, "retrieval", List.of());
        }

        String answer = grounded.text();
        String mode = "retrieval";
        Optional<String> rewritten = gemini.rewrite(clean, grounded.text());
        if (rewritten.isPresent()) {
            answer = rewritten.get();
            mode = "gemini";
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        for (ScoredChunk hit : strong) {
            scores.put(hit.chunk().id(), hit.score());
        }
        List<SourceCitation> sources = new ArrayList<>();
        for (Chunk chunk : grounded.used()) {
            sources.add(cite(chunk, scores.getOrDefault(chunk.id(), best)));
        }
        return remember(id, clean, answer, false, mode, sources);
    }

    private ChatResponse replyFromHeading(String sessionId, String message, String heading, boolean refused) {
        Chunk chunk = library.requireHeading(heading);
        GroundedAnswerer.GroundedAnswer grounded = GroundedAnswerer.compose(
                message,
                List.of(new ScoredChunk(chunk, 1.0)));
        String text = grounded.text().isBlank() ? excerpt(chunk.text(), 700) : grounded.text();
        List<Chunk> used = grounded.used().isEmpty() ? List.of(chunk) : grounded.used();
        List<SourceCitation> sources = used.stream().map(item -> cite(item, 1.0)).toList();
        return remember(sessionId, message, text, refused, "retrieval", sources);
    }

    private ChatResponse remember(
            String sessionId,
            String question,
            String answer,
            boolean refused,
            String mode,
            List<SourceCitation> sources
    ) {
        int maxHistory = Math.max(2, properties.getMaxHistory());
        List<String> titles = sources.stream().map(SourceCitation::title).toList();
        sessions.append(sessionId, new SessionStore.Turn("user", question, List.of()), maxHistory);
        sessions.append(sessionId, new SessionStore.Turn("assistant", answer, titles), maxHistory);
        return new ChatResponse(sessionId, answer, refused, mode, sources);
    }

    private String expand(String message, List<SessionStore.Turn> history) {
        if (history.isEmpty() || !needsContext(message)) {
            return message;
        }
        String previousUser = "";
        List<String> titles = List.of();
        for (int i = history.size() - 1; i >= 0; i--) {
            SessionStore.Turn turn = history.get(i);
            if ("user".equals(turn.role()) && previousUser.isBlank()) {
                previousUser = turn.content();
            }
            if ("assistant".equals(turn.role()) && titles.isEmpty()) {
                titles = turn.sourceTitles();
            }
        }
        return (message + " " + previousUser + " " + String.join(" ", titles)).trim();
    }

    private boolean lacksContextTerms(String message) {
        List<String> tokens = Tokenizer.tokens(message);
        int known = 0;
        int unknown = 0;
        for (String token : tokens) {
            if (index.knows(token)) {
                known++;
            } else {
                unknown++;
            }
        }
        return known == 0 || (unknown > 0 && known < 2);
    }

    private boolean needsContext(String message) {
        String trimmed = message.toLowerCase(Locale.ROOT).trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        return words <= 3 || FOLLOW_UP.matcher(trimmed).find();
    }

    private static SourceCitation cite(Chunk chunk, double score) {
        double rounded = Math.round(score * 1000.0) / 1000.0;
        return new SourceCitation(chunk.title(), chunk.documentTitle(), excerpt(chunk.text(), 280), rounded);
    }

    private static String excerpt(String text, int max) {
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() <= max) {
            return flat;
        }
        int end = flat.lastIndexOf(' ', max);
        if (end < max / 2) {
            end = max;
        }
        return flat.substring(0, end).trim() + "...";
    }
}
