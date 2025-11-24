package com.memora.adapter.out.kafka;

import com.memora.application.port.out.EventPublisher;
import com.memora.domain.MemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class KafkaEventPublisherAdapter implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisherAdapter.class);

    private final KafkaTemplate<String, MemoryEvent> kafkaTemplate;
    private final String topicName;

    public KafkaEventPublisherAdapter(
            KafkaTemplate<String, MemoryEvent> kafkaTemplate,
            @Value("${memora.kafka.topics.ingest}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @Override
    public void publish(MemoryEvent event) {
        String key = event.payload().metadata().getOrDefault("user_id", event.eventId().toString());

        log.debug("Sending event [{}] to topic [{}] with key [{}]", event.eventId(), topicName, key);

        // 2. Envoi Asynchrone
        CompletableFuture<SendResult<String, MemoryEvent>> future = 
            kafkaTemplate.send(topicName, key, event);

        // 3. Callback (Fire & Forget intelligent)
        // On ne bloque pas le thread principal, mais on loggue le résultat.
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ Event [{}] sent successfully to partition {}", 
                    event.eventId(), result.getRecordMetadata().partition());
            } else {
                // Ici, dans un vrai système, on pourrait envoyer vers une Dead Letter Queue locale
                // ou incrémenter une métrique Prometheus "kafka.producer.error"
                log.error("❌ Failed to send event [{}]", event.eventId(), ex);
            }
        });
    }
}