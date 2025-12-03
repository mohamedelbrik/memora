package com.memora.adapter.in.web;

import com.memora.application.port.in.ChatMemoryUseCase;
import com.memora.domain.ChatRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
        String answer = chatMemoryUseCase.chat(
                request.question(),
                request.userId(),
                request.userName()
        );
        return Map.of("answer", answer);
    }
    // NOUVEL ENDPOINT
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return chatMemoryUseCase.chatStream(
                request.question(),
                request.userId(),
                request.userName()
        );
    }
}