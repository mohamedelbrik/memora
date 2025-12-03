package com.memora.adapter.out.persistence;

import com.memora.application.port.out.MemoryRepository;
import com.memora.application.service.DateExtractionService;
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

import java.time.LocalDate;
import java.util.*;
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

    // TODO To be removed
    @Override
    public void save(MemoryEvent event, float[] embedding) {
        Document document = new Document(
            event.payload().content(),
            Map.of(
                "user_id", event.payload().metadata().getOrDefault("user_id", "unknown"),
                "source", event.source().toString(),
                "original_event_id", event.eventId().toString(),
                "ingested_at", event.timestamp().toString()
            )
        );

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
                        doc.getMetadata().containsKey("distance")
                                ? 1.0 - ((Number) doc.getMetadata().get("distance")).doubleValue()
                                : null,
                        null
                ))
                .collect(Collectors.toList());
    }

    // Nouvelle signature : DateRange au lieu de LocalDate
    public List<Memory> searchHybrid(String query, int limit, DateExtractionService.DateRange dateRange) {

        float[] queryVector = embeddingModel.embed(query);
        String queryVectorStr = java.util.Arrays.toString(queryVector);
        String lexicalQuery = query.replaceAll("[^a-zA-Z0-9ä-ü\\s]", "").trim().replaceAll("\\s+", " | ");
        int poolSize = limit * 2;

        String dateClause = "";

        // --- LOGIQUE FILTRE DATE ---
        if (dateRange != null) {
            // On filtre entre le début (00:00) et la fin (23:59 du jour de fin)
            // On utilise CAST pour éviter les erreurs JDBC
            dateClause = " AND ingested_at >= CAST(? AS timestamptz) AND ingested_at < CAST(? AS timestamptz) + INTERVAL '1 day' ";
        }
        // ---------------------------

        String sql = """
            WITH semantic_rank AS (
                SELECT id, RANK() OVER (ORDER BY embedding <=> CAST(? AS vector)) as rank
                FROM vector_store
                WHERE 1=1 %s 
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
            ),
            lexical_rank AS (
                SELECT id, RANK() OVER (ORDER BY ts_rank(content_search, to_tsquery('simple', ?)) DESC) as rank
                FROM vector_store
                WHERE content_search @@ to_tsquery('simple', ?)
                %s 
                LIMIT ?
            )
            SELECT v.id, v.content, v.metadata,
                   COALESCE(1.0 / (60 + s.rank), 0.0) + (10.0 * COALESCE(1.0 / (60 + l.rank), 0.0)) as rrf_score
            FROM vector_store v
            LEFT JOIN semantic_rank s ON v.id = s.id
            LEFT JOIN lexical_rank l ON v.id = l.id
            WHERE s.id IS NOT NULL OR l.id IS NOT NULL
            ORDER BY rrf_score DESC
            LIMIT ?
        """.formatted(dateClause, dateClause);

        List<Object> args = new ArrayList<>();

        // -- SEMANTIC --
        args.add(queryVectorStr);
        if (dateRange != null) {
            args.add(dateRange.start()); // ? 1
            args.add(dateRange.end());   // ? 2
        }
        args.add(queryVectorStr);
        args.add(poolSize);

        // -- LEXICAL --
        args.add(lexicalQuery);
        args.add(lexicalQuery);
        if (dateRange != null) {
            args.add(dateRange.start()); // ? 1
            args.add(dateRange.end());   // ? 2
        }
        args.add(poolSize);

        // -- LIMIT --
        args.add(limit);

        return jdbcTemplate.query(sql, args.toArray(), (rs, rowNum) -> new Memory(
                UUID.fromString(rs.getString("id")),
                rs.getString("content"),
                null,
                rs.getDouble("rrf_score"),
                null
        ));
    }

    // Nouvelle méthode pour le mode "Journal / Timeline"
    public List<Memory> findByDateRange(DateExtractionService.DateRange range) {
        String sql = """
            SELECT id, content, metadata, 1.0 as score
            FROM vector_store
            WHERE ingested_at >= CAST(? AS timestamptz) 
              AND ingested_at < CAST(? AS timestamptz) + INTERVAL '1 day'
            ORDER BY ingested_at ASC
        """;

        return jdbcTemplate.query(sql,
                new Object[]{ range.start(), range.end() },
                (rs, rowNum) -> new Memory(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("content"),
                        null, // Metadata mapping simplifié
                        1.0,  // Score max car c'est une correspondance exacte de date
                        null
                )
        );
    }

    @Override
    public int countMemoriesByKeyword(String keyword) {
        return 0;
    }
}