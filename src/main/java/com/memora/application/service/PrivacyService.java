package com.memora.application.service;

import com.memora.application.port.out.MemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);
    private final MemoryRepository memoryRepository;

    public PrivacyService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Transactional // Important : Tout ou rien
    public void deleteUserHistory(String userId) {
        log.warn("⚠️ TRIGGERING DATA DELETION FOR USER: {}", userId);
        memoryRepository.deleteByUserId(userId);
    }
}