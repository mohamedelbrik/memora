package com.memora.adapter.out.persistence;

import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.Memory;
import com.memora.domain.MemoryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PgVectorMemoryRepository implements MemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(PgVectorMemoryRepository.class);
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public PgVectorMemoryRepository(VectorStore vectorStore,  JdbcTemplate jdbcTemplate,  EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
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

    // N'oublie pas d'injecter JdbcTemplate via le constructeur !
    // private final JdbcTemplate jdbcTemplate;

    public List<Memory> searchHybrid(String query, int limit) {

        float[] queryVector = embeddingModel.embed(query);

        String lexicalQuery = query.replaceAll("[^a-zA-Z0-9ä-ü\\s]", "").trim().replaceAll("\\s+", " | ");
        // On sélectionne s.rank et l.rank pour le debugging
        String sql = """
            WITH semantic_rank AS (
                SELECT id, 
                       RANK() OVER (ORDER BY embedding <=> ?::vector) as rank
                FROM vector_store
                ORDER BY embedding <=> ?::vector
                LIMIT ?
            ),
            lexical_rank AS (
               SELECT id,\s
                      -- On utilise to_tsquery au lieu de plainto_tsquery pour supporter la syntaxe avec '|'
                      RANK() OVER (ORDER BY ts_rank(content_search, to_tsquery('simple', ?)) DESC) as rank
               FROM vector_store
               WHERE content_search @@ to_tsquery('simple', ?)
               LIMIT ?
           )
           SELECT v.id, v.content, v.metadata,
                  s.rank as sem_rank,
                  l.rank as lex_rank,
                  COALESCE(1.0 / (60 + s.rank), 0.0) + (10.0 * COALESCE(1.0 / (60 + l.rank), 0.0)) as rrf_score
           FROM vector_store v
           LEFT JOIN semantic_rank s ON v.id = s.id
           LEFT JOIN lexical_rank l ON v.id = l.id
           WHERE s.id IS NOT NULL OR l.id IS NOT NULL
           ORDER BY rrf_score DESC
           LIMIT ?
        """;

        int candidatePoolSize = limit * 2;

        return jdbcTemplate.query(sql,
                new Object[]{
                        queryVector, queryVector, candidatePoolSize,
                        lexicalQuery, lexicalQuery, candidatePoolSize, // <--- On passe la version "OR" ici
                        limit
                },
                (rs, rowNum) -> {
                    // --- ZONE DE DEBUG / TÉLÉMÉTRIE ---
                    String content = rs.getString("content");
                    double score = rs.getDouble("rrf_score");
                    // getLong renvoie 0 si c'est NULL en SQL (donc non trouvé)
                    long semRank = rs.getLong("sem_rank");
                    long lexRank = rs.getLong("lex_rank");

                    String snippet = content.length() > 50 ? content.substring(0, 50) + "..." : content;

                    // On affiche un tableau propre dans la console
                    System.out.printf("📊 [RRF DEBUG] Score: %.6f | SemRank: %-4d | LexRank: %-4d | %s%n",
                            score, semRank, lexRank, snippet);
                    // -----------------------------------

                    return new Memory(
                            UUID.fromString(rs.getString("id")),
                            content,
                            null,
                            score,
                            null
                    );
                }
        );
    }
}