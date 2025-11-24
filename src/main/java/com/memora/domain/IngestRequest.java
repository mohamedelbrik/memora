package com.memora.domain;

import java.util.Map;

public record IngestRequest(
        String content,
        String sourceType,
        String contentType,
        Map<String, String> metadata
    ) {}