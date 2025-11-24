package com.memora.application.port.in;

public interface ChatMemoryUseCase {
    String chat(String query, String userId, String userName);
}