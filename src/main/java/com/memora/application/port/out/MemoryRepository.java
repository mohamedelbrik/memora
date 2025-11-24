package com.memora.application.port.out;

import com.memora.domain.Memory;
import com.memora.domain.MemoryEvent;
import java.util.List;

public interface MemoryRepository {
    void save(MemoryEvent memory, float[] embedding);
    
    // NOUVELLE MÉTHODE
    List<Memory> findSimilar(String query, int limit, double minScore);
}