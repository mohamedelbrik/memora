
package com.memora.application.service;

import com.memora.application.port.in.IngestMemoryUseCase;
import com.memora.application.port.out.EventPublisher;
import com.memora.domain.ContentType;
import com.memora.domain.MemoryEvent;
import com.memora.domain.MemoryPayload;
import com.memora.domain.SourceType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MemoryIngestionService implements IngestMemoryUseCase {

    private final EventPublisher eventPublisher;

    public MemoryIngestionService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID ingest(IngestCommand command) {
        // 1. Validation métier (Un Tech Lead valide TOUJOURS ses entrées)
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("Memory content cannot be empty");
        }

        // 2. Mapping de la Commande vers l'Objet du Domaine
        // On transforme les Strings brutes en Enums typés (Fail Fast si le type est inconnu)
        var event = new MemoryEvent(
            UUID.randomUUID(),
            Instant.now(),
            SourceType.valueOf(command.sourceType()),
            ContentType.valueOf(command.contentType()),
            new MemoryPayload(command.content(), command.metadata())
        );

        // 3. Appel du port de sortie (On ne sait pas que c'est Kafka derrière, et on s'en fiche)
        eventPublisher.publish(event);

        // 4. Retourne l'ID pour que l'API puisse répondre "202 Accepted - ID: xyz"
        return event.eventId();
    }
}