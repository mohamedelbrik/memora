package com.memora.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Représente un souvenir brut capturé par le système.
 * Utilisation d'un Java Record pour l'immutabilité (Best Practice Kafka).
 */
public record MemoryEvent(
    UUID eventId,
    Instant timestamp,
    SourceType source,
    ContentType type,
    MemoryPayload payload
) {
    // Compact constructor for validating data at creation (Design Pattern "Fail Fast")
    public MemoryEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
