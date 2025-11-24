package com.memora.adapter.in.web;

import com.memora.application.port.in.ChatMemoryUseCase;
import com.memora.domain.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class MemoryChatController {

    private final ChatMemoryUseCase chatMemoryUseCase;

    public MemoryChatController(ChatMemoryUseCase chatMemoryUseCase) {
        this.chatMemoryUseCase = chatMemoryUseCase;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        // On passe les infos au UseCase
        String answer = chatMemoryUseCase.chat(
                request.question(),
                request.userId(),
                request.userName()
        );
        return Map.of("answer", answer);
    }
}