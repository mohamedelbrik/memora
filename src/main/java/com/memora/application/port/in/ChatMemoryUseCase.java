package com.memora.application.port.in;

import reactor.core.publisher.Flux;

public interface ChatMemoryUseCase {
    String chat(String query, String userId, String userName);
    // LA NOUVELLE MÉTHODE
    Flux<String> chatStream(String query, String userId, String userName);
}