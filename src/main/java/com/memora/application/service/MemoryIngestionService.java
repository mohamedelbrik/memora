
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
        if (command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("Memory content cannot be empty");
        }

        var event = new MemoryEvent(
            UUID.randomUUID(),
            Instant.now(),
            SourceType.valueOf(command.sourceType()),
            ContentType.valueOf(command.contentType()),
            new MemoryPayload(command.content(), command.metadata())
        );

        eventPublisher.publish(event);

        return event.eventId();
    }
}