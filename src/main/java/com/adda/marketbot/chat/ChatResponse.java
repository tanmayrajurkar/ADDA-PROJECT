package com.adda.marketbot.chat;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        boolean refused,
        String mode,
        List<SourceCitation> sources
) {
}
