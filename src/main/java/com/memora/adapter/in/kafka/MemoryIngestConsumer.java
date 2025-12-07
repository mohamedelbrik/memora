
package com.memora.adapter.in.kafka;

import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.MemoryEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemoryIngestConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemoryIngestConsumer.class);

    private final EmbeddingModel embeddingModel;
    private final MemoryRepository memoryRepository;
    private final MeterRegistry meterRegistry;

    public MemoryIngestConsumer(EmbeddingModel embeddingModel, MemoryRepository memoryRepository, MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.memoryRepository = memoryRepository;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "${memora.kafka.topics.ingest}", groupId = "memora-group")
    public void process(MemoryEvent event) {
        log.info("🧠 Processing memory event: {}", event.eventId());
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            String content = event.payload().content();

            // Spring AI 1.0.0+ returns a float[] directly, no need to convert!
            float[] vector = embeddingModel.embed(content);

            log.debug("Computed embedding vector size: {}", vector.length);

            memoryRepository.save(event, vector);

            log.info("✅ Memory [{}] fully processed and indexed.", event.eventId());
            // On arrête le chrono et on enregistre
            sample.stop(Timer.builder("memora.ingestion.latency")
                    .description("Temps pris pour vectoriser et stocker un souvenir")
                    .tag("source", event.source().toString()) // On tague par source (WEB, MOBILE...)
                    .register(meterRegistry));

            // On incrémente un compteur
            meterRegistry.counter("memora.memories.ingested", "status", "success").increment();

        } catch (Exception e) {
            log.error("❌ Error processing memory event [{}]: {}", event.eventId(), e.getMessage(), e);
            meterRegistry.counter("memora.memories.ingested", "status", "error").increment();
        }
    }
}