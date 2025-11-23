package com.memora.domain;

import java.util.Map;

public record MemoryPayload(
        String content,
        Map<String, String> metadata
) {}
