package com.memora.adapter.out.persistence;

import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.Memory;
import com.memora.domain.MemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PgVectorMemoryRepository implements MemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(PgVectorMemoryRepository.class);
    private final VectorStore vectorStore;

    public PgVectorMemoryRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void save(MemoryEvent event, float[] embedding) {
        // 1. Conversion du Domain Event vers le Document Spring AI
        // Note: Spring AI gère normalement l'embedding lui-même lors du "add".
        // Mais comme on l'a pré-calculé dans le consumer (pour le contrôle), 
        // on va ici créer un Document enrichi.
        
        Document document = new Document(
            event.payload().content(),
            Map.of(
                "user_id", event.payload().metadata().getOrDefault("user_id", "unknown"),
                "source", event.source().toString(),
                "original_event_id", event.eventId().toString(),
                "ingested_at", event.timestamp().toString()
            )
        );

        // Note Technique pour l'entretien : 
        // Ici, vectorStore.add() va potentiellement ré-appeler l'embedding model si on ne fait pas attention.
        // Dans une version optimisée "Prod", on utiliserait JdbcTemplate pour insérer directement le vecteur 'embedding'
        // passé en paramètre pour économiser des tokens OpenAI.
        // Pour ce MVP, on utilise l'abstraction standard pour aller vite.
        
        vectorStore.add(List.of(document));
        
        log.info("💾 Memory [{}] saved to Postgres with Metadata", event.eventId());
    }

    @Override
    public List<Memory> findSimilar(String query, int limit, double minScore) {
        // Appel magique à Spring AI + Postgres
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.query(query)
                        .withTopK(limit) // Nombre de résultats max
                        .withSimilarityThreshold(minScore) // Score minimum (0.0 à 1.0)
        );

        // Mapping Document (Spring AI) -> Memory (Domain)
        return documents.stream()
                .map(doc -> new Memory(
                        UUID.fromString(doc.getId()),
                        doc.getContent(),
                        doc.getMetadata(),
                        // --- CORRECTION ICI ---
                        // On cast en Number d'abord pour gérer Float ou Double sans planter
                        doc.getMetadata().containsKey("distance")
                                ? 1.0 - ((Number) doc.getMetadata().get("distance")).doubleValue()
                                : null,
                        null
                ))
                .collect(Collectors.toList());
    }
}