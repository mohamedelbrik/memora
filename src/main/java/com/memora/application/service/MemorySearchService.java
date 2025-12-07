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

    // TODO to be remove
//    @Override
//    public List<Memory> search(String query) {
//        // Business rule: we want the 5 most relevant with a score > 0.5
//        return memoryRepository.findSimilar(query, 10, 0.3);
//    }
}