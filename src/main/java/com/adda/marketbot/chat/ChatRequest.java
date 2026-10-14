package com.adda.marketbot.chat;

public record ChatRequest(
        @jakarta.validation.constraints.Size(max = 64) String sessionId,
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 1000) String message
) {
}
