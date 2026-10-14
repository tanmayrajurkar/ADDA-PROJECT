package com.adda.marketbot.retrieval;

import com.adda.marketbot.knowledge.KnowledgeLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalTest {

    private final TfidfIndex index = new TfidfIndex(new KnowledgeLibrary());

    @Test
    void niftyQuestionRanksTheNiftyPassage() {
        var hits = index.search("What is the Nifty 50?", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).chunk().title().contains("Nifty 50"), hits.toString());
        assertTrue(hits.get(0).score() >= 0.12, hits.toString());
    }

    @Test
    void sebiQuestionRanksTheRegulator() {
        var hits = index.search("Who regulates the Indian securities market?", 3);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).chunk().title().contains("SEBI") || hits.get(0).chunk().text().contains("SEBI"), hits.toString());
    }

    @Test
    void weatherHasNoUsableContext() {
        var hits = index.search("What is the weather in Mumbai tomorrow?", 1);
        assertTrue(hits.isEmpty() || hits.get(0).score() < 0.12, hits.toString());
    }
}
