package com.adda.marketbot.retrieval;

import com.adda.marketbot.knowledge.Chunk;
import com.adda.marketbot.knowledge.KnowledgeLibrary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TfidfIndex {

    private final List<Chunk> chunks;
    private final List<Map<String, Double>> vectors;
    private final List<Double> norms;
    private final Map<String, Double> idf;

    public TfidfIndex(KnowledgeLibrary library) {
        this.chunks = library.chunks();
        int n = chunks.size();
        List<Map<String, Integer>> counts = new ArrayList<>(n);
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Chunk chunk : chunks) {
            Map<String, Integer> count = new HashMap<>();
            String weighted = (chunk.title() + " ").repeat(3) + chunk.text();
            for (String token : Tokenizer.tokens(weighted)) {
                count.merge(token, 1, Integer::sum);
            }
            counts.add(count);
            for (String term : count.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        Map<String, Double> idfValues = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            double value = Math.log((n + 1.0) / (entry.getValue() + 1.0)) + 1.0;
            idfValues.put(entry.getKey(), value);
        }
        this.idf = Map.copyOf(idfValues);

        List<Map<String, Double>> builtVectors = new ArrayList<>(n);
        List<Double> builtNorms = new ArrayList<>(n);
        for (Map<String, Integer> count : counts) {
            Map<String, Double> vector = new HashMap<>();
            double normSquared = 0;
            for (Map.Entry<String, Integer> entry : count.entrySet()) {
                double weight = (1.0 + Math.log(entry.getValue())) * idf.getOrDefault(entry.getKey(), 0.0);
                vector.put(entry.getKey(), weight);
                normSquared += weight * weight;
            }
            builtVectors.add(Map.copyOf(vector));
            builtNorms.add(Math.sqrt(normSquared));
        }
        this.vectors = List.copyOf(builtVectors);
        this.norms = List.copyOf(builtNorms);
    }

    public int size() {
        return chunks.size();
    }

    public List<ScoredChunk> search(String query, int limit) {
        Map<String, Double> queryVector = queryVector(query);
        double queryNorm = norm(queryVector);
        if (queryNorm == 0 || limit <= 0) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double docNorm = norms.get(i);
            if (docNorm == 0) {
                continue;
            }
            double dot = 0;
            for (Map.Entry<String, Double> entry : queryVector.entrySet()) {
                Double docWeight = vectors.get(i).get(entry.getKey());
                if (docWeight != null) {
                    dot += entry.getValue() * docWeight;
                }
            }
            double score = dot / (queryNorm * docNorm);
            if (score > 0) {
                scored.add(new ScoredChunk(chunks.get(i), score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (scored.size() > limit) {
            return List.copyOf(scored.subList(0, limit));
        }
        return List.copyOf(scored);
    }

    public boolean knows(String term) {
        return idf.containsKey(term);
    }

    private Map<String, Double> queryVector(String query) {
        Map<String, Integer> counts = new HashMap<>();
        for (String token : Tokenizer.tokens(query)) {
            if (idf.containsKey(token)) {
                counts.merge(token, 1, Integer::sum);
            }
        }
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            double weight = (1.0 + Math.log(entry.getValue())) * idf.get(entry.getKey());
            vector.put(entry.getKey(), weight);
        }
        return vector;
    }

    private static double norm(Map<String, Double> vector) {
        double sum = 0;
        for (double weight : vector.values()) {
            sum += weight * weight;
        }
        return Math.sqrt(sum);
    }
}
