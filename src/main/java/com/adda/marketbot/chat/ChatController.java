package com.adda.marketbot.chat;

import com.adda.marketbot.knowledge.Topic;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.sessionId(), request.message());
    }

    @GetMapping("/topics")
    public List<Topic> topics() {
        return chatService.topics();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return chatService.health();
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> clear(@PathVariable String id) {
        chatService.clear(id);
        return ResponseEntity.noContent().build();
    }
}
