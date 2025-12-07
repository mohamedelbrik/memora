package com.memora.adapter.in.web;

import com.memora.application.port.in.IngestMemoryUseCase;
import com.memora.application.port.in.IngestMemoryUseCase.IngestCommand;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
public class SeederController {

    private final IngestMemoryUseCase ingestMemoryUseCase;

    public SeederController(IngestMemoryUseCase ingestMemoryUseCase) {
        this.ingestMemoryUseCase = ingestMemoryUseCase;
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedData() {
        ZonedDateTime now = ZonedDateTime.now();
        
        // Dates relatives pour que le test temporel marche toujours
        String today = now.format(DateTimeFormatter.ISO_INSTANT);
        String yesterday = now.minusDays(1).format(DateTimeFormatter.ISO_INSTANT);
        String dayBeforeYesterday = now.minusDays(2).format(DateTimeFormatter.ISO_INSTANT);

        List<IngestCommand> scenarios = List.of(
            // --- AVANT-HIER (J-2) ---
            new IngestCommand(
                "J ai migré le cluster Kafka vers le mode KRaft pour supprimer la dépendance à Zookeeper. Le démarrage est 3x plus rapide.",
                "NOTE", "TEXT",
                Map.of("user_id", "Mohamed", "source", "Obsidian", "ingested_at", dayBeforeYesterday)
            ),
            new IngestCommand(
                "Soirée d anniversaire surprise organisée par Fatima Zahra. On a mangé un excellent tajine au restaurant Le Sud.",
                "WEB", "TEXT",
                Map.of("user_id", "Mohamed", "source", "Calendar", "ingested_at", dayBeforeYesterday)
            ),

            // --- HIER (J-1) ---
            new IngestCommand(
                "Incident critique sur la prod : la base Postgres était verrouillée à cause d une transaction IDLE in transaction qui bloquait les writes.",
                "LOGS", "TEXT",
                Map.of("user_id", "Mohamed", "source", "Datadog", "ingested_at", yesterday)
            ),
            new IngestCommand(
                "Revue de code sur le consumer Kafka. On a ajouté un Dead Letter Topic (DLT) pour gérer les messages empoisonnés sans bloquer le flux.",
                "NOTE", "TEXT",
                Map.of("user_id", "Mohamed", "source", "GitLab", "ingested_at", yesterday)
            ),

            // --- AUJOURD'HUI (J-0) ---
            new IngestCommand(
                "Séance de sport ce matin : 10km de course à pied en 52 minutes. Bonne préparation pour le semi-marathon de Paris.",
                "MOBILE_APP", "TEXT",
                Map.of("user_id", "Mohamed", "source", "Strava", "ingested_at", today)
            ),
            new IngestCommand(
                "Discussion avec l équipe architecture sur le dimensionnement des partitions Kafka. On va passer à 32 partitions pour tenir la charge du Black Friday.",
                "WEB", "TEXT",
                Map.of("user_id", "Mohamed", "source", "Slack", "ingested_at", today)
            ),
            new IngestCommand(
                "Avancée majeure sur le projet Memora : implémentation de l Agentic RAG avec Spring AI. Le système est maintenant capable d utiliser des outils SQL de manière autonome.",
                "NOTE", "TEXT",
                Map.of("user_id", "Mohamed", "source", "IDE", "ingested_at", today)
            ),
            new IngestCommand(
                "Lu un article intéressant sur l architecture Data Mesh. Ça pourrait être pertinent pour décentraliser la gouvernance des données chez Decathlon.",
                "WEB", "TEXT",
                Map.of("user_id", "Mohamed", "source", "Medium", "ingested_at", today)
            )
        );

        // Injection en masse
        scenarios.forEach(ingestMemoryUseCase::ingest);

        return ResponseEntity.ok("✅ 8 Souvenirs injectés avec succès (Avant-hier, Hier, Aujourd'hui).");
    }
}