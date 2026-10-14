package com.adda.marketbot.chat;

import com.adda.marketbot.knowledge.Chunk;
import com.adda.marketbot.retrieval.ScoredChunk;
import com.adda.marketbot.retrieval.Tokenizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class GroundedAnswerer {

    private GroundedAnswerer() {
    }

    static GroundedAnswer compose(String question, List<ScoredChunk> hits) {
        if (hits.isEmpty()) {
            return new GroundedAnswer("", List.of());
        }
        Set<String> queryTokens = new LinkedHashSet<>(Tokenizer.tokens(question));
        double bestOverlap = 0;
        List<Candidate> all = new ArrayList<>();

        for (int chunkIndex = 0; chunkIndex < hits.size(); chunkIndex++) {
            Chunk chunk = hits.get(chunkIndex).chunk();
            String[] sentences = chunk.text().split("(?<=[.!?])\\s+(?=[A-Z])");
            for (int sentenceIndex = 0; sentenceIndex < sentences.length; sentenceIndex++) {
                String sentence = sentences[sentenceIndex].replaceAll("\\s+", " ").trim();
                if (sentence.length() < 20) {
                    continue;
                }
                double overlap = overlap(queryTokens, sentence);
                Candidate candidate = new Candidate(chunkIndex, sentenceIndex, sentence, overlap, chunk);
                all.add(candidate);
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                }
            }
        }

        double floor = Math.max(0.2, bestOverlap * 0.55);
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : all) {
            boolean openingOfBestChunk = candidate.chunkIndex == 0 && candidate.sentenceIndex < 3;
            if (openingOfBestChunk || candidate.overlap >= floor) {
                selected.add(candidate);
            }
        }

        selected.sort((left, right) -> {
            int byChunk = Integer.compare(left.chunkIndex, right.chunkIndex);
            return byChunk != 0 ? byChunk : Integer.compare(left.sentenceIndex, right.sentenceIndex);
        });

        List<String> sentences = new ArrayList<>();
        List<Chunk> used = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        for (Candidate candidate : selected) {
            if (sentences.size() == 5 || answer.length() > 900) {
                break;
            }
            if (sentences.contains(candidate.sentence)) {
                continue;
            }
            sentences.add(candidate.sentence);
            if (!used.contains(candidate.chunk)) {
                used.add(candidate.chunk);
            }
            if (!answer.isEmpty()) {
                answer.append(' ');
            }
            answer.append(candidate.sentence);
        }
        return new GroundedAnswer(answer.toString().trim(), List.copyOf(used));
    }

    private static double overlap(Set<String> queryTokens, String sentence) {
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> sentenceTokens = new LinkedHashSet<>(Tokenizer.tokens(sentence));
        int hits = 0;
        for (String token : queryTokens) {
            if (sentenceTokens.contains(token) || sentence.toLowerCase(Locale.ROOT).contains(token.replace('_', ' '))) {
                hits++;
            }
        }
        return (double) hits / queryTokens.size();
    }

    private record Candidate(int chunkIndex, int sentenceIndex, String sentence, double overlap, Chunk chunk) {
    }

    record GroundedAnswer(String text, List<Chunk> used) {
    }
}
