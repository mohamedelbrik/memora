
package com.memora.adapter.in.kafka;

import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.MemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryIngestConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemoryIngestConsumer.class);

    private final EmbeddingModel embeddingModel; // L'interface Spring AI (Ollama ici)
    private final MemoryRepository memoryRepository;

    public MemoryIngestConsumer(EmbeddingModel embeddingModel, MemoryRepository memoryRepository) {
        this.embeddingModel = embeddingModel;
        this.memoryRepository = memoryRepository;
    }

    // On écoute le topic défini dans application.yml
    @KafkaListener(topics = "${memora.kafka.topics.ingest}", groupId = "memora-group")
    public void process(MemoryEvent event) {
        log.info("🧠 Processing memory event: {}", event.eventId());

        try {
            String content = event.payload().content();

            // 1. EMBEDDING (Direct et Optimisé)
            // Spring AI 1.0.0+ renvoie directement un float[], plus besoin de convertir !
            float[] vector = embeddingModel.embed(content);

            log.debug("Computed embedding vector size: {}", vector.length);

            // 2. PERSISTANCE
            memoryRepository.save(event, vector);

            log.info("✅ Memory [{}] fully processed and indexed.", event.eventId());

        } catch (Exception e) {
            log.error("❌ Error processing memory event [{}]: {}", event.eventId(), e.getMessage(), e);
        }
    }
}