package com.memora.adapter.out.persistence;

import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.MemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
}