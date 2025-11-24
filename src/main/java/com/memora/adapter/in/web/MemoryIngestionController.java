package com.memora.adapter.in.web;

import com.memora.application.port.in.IngestMemoryUseCase;
import com.memora.application.port.in.IngestMemoryUseCase.IngestCommand;
import com.memora.domain.IngestRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memories")
public class MemoryIngestionController {

    private final IngestMemoryUseCase ingestMemoryUseCase;

    public MemoryIngestionController(IngestMemoryUseCase ingestMemoryUseCase) {
        this.ingestMemoryUseCase = ingestMemoryUseCase;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> ingest(@RequestBody IngestRequest request) {
        var command = new IngestCommand(
            request.content(),
            request.sourceType(),
            request.contentType(),
            request.metadata()
        );

        UUID memoryId = ingestMemoryUseCase.ingest(command);

        // 3. Réponse 202 Accepted
        // Pourquoi 202 ? Parce que le traitement est asynchrone (Kafka).
        // On dit au client : "J'ai bien reçu, je m'en occupe", mais ce n'est pas encore "fini".
        return ResponseEntity.accepted().body(Map.of("id", memoryId));
    }
}