
package com.memora.application.port.in;

import java.util.Map;
import java.util.UUID;

public interface IngestMemoryUseCase {
    UUID ingest(IngestCommand command);

    // Record local pour transporter la demande (DTO interne)
    record IngestCommand(
        String content,
        String sourceType, // "WEB", "MOBILE"...
        String contentType, // "TEXT", "AUDIO"...
        Map<String, String> metadata
    ) {}
}