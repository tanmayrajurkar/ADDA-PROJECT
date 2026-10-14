package com.adda.marketbot.knowledge;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeLibrary {

    private static final List<String> FILES = List.of(
            "knowledge/01-market-structure.md",
            "knowledge/02-indices.md",
            "knowledge/03-trading-and-orders.md",
            "knowledge/04-accounts-and-participants.md",
            "knowledge/05-corporate-actions.md",
            "knowledge/06-derivatives.md",
            "knowledge/07-terms-and-taxes.md",
            "knowledge/08-protection-and-scope.md"
    );

    private final List<Topic> topics;
    private final List<Chunk> chunks;
    private final Map<String, Chunk> byHeading;

    public KnowledgeLibrary() {
        this(loadDocuments());
    }

    KnowledgeLibrary(List<ParsedDocument> documents) {
        List<Topic> topicList = new ArrayList<>();
        List<Chunk> chunkList = new ArrayList<>();
        Map<String, Chunk> headings = new LinkedHashMap<>();
        for (ParsedDocument document : documents) {
            topicList.add(new Topic(document.id(), document.title(), document.summary()));
            for (Chunk chunk : document.chunks()) {
                chunkList.add(chunk);
                headings.put(chunk.title(), chunk);
            }
        }
        this.topics = List.copyOf(topicList);
        this.chunks = List.copyOf(chunkList);
        this.byHeading = Map.copyOf(headings);
    }

    public List<Topic> topics() {
        return topics;
    }

    public List<Chunk> chunks() {
        return chunks;
    }

    public Chunk requireHeading(String heading) {
        Chunk chunk = byHeading.get(heading);
        if (chunk == null) {
            throw new IllegalStateException("Knowledge base is missing the section: " + heading);
        }
        return chunk;
    }

    private static List<ParsedDocument> loadDocuments() {
        List<ParsedDocument> documents = new ArrayList<>();
        for (String path : FILES) {
            try (InputStream in = KnowledgeLibrary.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Missing knowledge file: " + path);
                }
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                documents.add(parse(path, raw));
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read " + path, ex);
            }
        }
        return documents;
    }

    static ParsedDocument parse(String path, String raw) {
        String id = path.substring(path.lastIndexOf('/') + 1).replace(".md", "");
        String documentTitle = id;
        List<Chunk> chunks = new ArrayList<>();
        String currentHeading = null;
        StringBuilder body = new StringBuilder();

        for (String line : raw.split("\n", -1)) {
            if (line.startsWith("# ") && !line.startsWith("## ")) {
                documentTitle = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("## ")) {
                flush(id, documentTitle, currentHeading, body, chunks);
                currentHeading = line.substring(3).trim();
                body = new StringBuilder();
                continue;
            }
            if (currentHeading != null) {
                body.append(line).append('\n');
            }
        }
        flush(id, documentTitle, currentHeading, body, chunks);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("No sections found in " + path);
        }
        String summary = firstSentence(chunks.get(0).text());
        return new ParsedDocument(id, documentTitle, summary, List.copyOf(chunks));
    }

    private static void flush(String id, String documentTitle, String heading, StringBuilder body, List<Chunk> chunks) {
        if (heading == null) {
            return;
        }
        String text = body.toString().trim().replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        if (text.length() < 40) {
            return;
        }
        String chunkId = id + "-" + (chunks.size() + 1);
        chunks.add(new Chunk(chunkId, documentTitle, heading, text));
    }

    private static String firstSentence(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        int end = flat.indexOf(". ");
        String sentence = end > 0 ? flat.substring(0, end + 1) : flat;
        if (sentence.length() > 180) {
            return sentence.substring(0, 177).trim() + "...";
        }
        return sentence;
    }

    record ParsedDocument(String id, String title, String summary, List<Chunk> chunks) {
    }
}
