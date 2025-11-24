package com.memora.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Memory(
    UUID id,
    String content,
    Map<String, Object> metadata,
    Double relevanceScore, // Le score de similarité (ex: 0.85)
    Instant createdAt
) {}