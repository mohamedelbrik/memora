package com.memora.application.port.out;

import com.memora.application.service.DateExtractionService;
import com.memora.domain.Memory;
import com.memora.domain.MemoryEvent;

import java.time.LocalDate;
import java.util.List;

public interface MemoryRepository {
    void save(MemoryEvent memory, float[] embedding);

    // TODO To be removed
    List<Memory> findSimilar(String query, int limit, double minScore);
    List<Memory> searchHybrid(String query, int limit, DateExtractionService.DateRange dateRange);

    // La méthode Timeline
    List<Memory> findByDateRange(DateExtractionService.DateRange range);

    // --- AJOUTE CELLE-CI ---
    int countMemoriesByKeyword(String keyword);

}