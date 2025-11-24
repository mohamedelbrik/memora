package com.memora.application.service;

import com.memora.application.port.in.SearchMemoryUseCase;
import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.Memory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemorySearchService implements SearchMemoryUseCase {

    private final MemoryRepository memoryRepository;

    public MemorySearchService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Override
    public List<Memory> search(String query) {
        // Règle métier : on veut les 5 plus pertinents avec un score > 0.5
        return memoryRepository.findSimilar(query, 5, 0.3);
    }
}