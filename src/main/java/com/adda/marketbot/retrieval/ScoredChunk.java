package com.adda.marketbot.retrieval;

import com.adda.marketbot.knowledge.Chunk;

public record ScoredChunk(Chunk chunk, double score) {
}
