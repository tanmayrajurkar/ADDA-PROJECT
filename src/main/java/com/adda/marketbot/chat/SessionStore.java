package com.adda.marketbot.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class SessionStore {

    record Turn(String role, String content, List<String> sourceTitles) {
    }

    private final ConcurrentHashMap<String, List<Turn>> sessions = new ConcurrentHashMap<>();

    List<Turn> history(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of());
    }

    void append(String sessionId, Turn turn, int maxHistory) {
        sessions.compute(sessionId, (id, existing) -> {
            List<Turn> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.add(turn);
            if (next.size() > maxHistory) {
                next = new ArrayList<>(next.subList(next.size() - maxHistory, next.size()));
            }
            return List.copyOf(next);
        });
    }

    void clear(String sessionId) {
        sessions.remove(sessionId);
    }
}
