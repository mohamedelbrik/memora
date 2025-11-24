package com.memora.application.port.out;

import com.memora.domain.MemoryEvent;

public interface MemoryRepository {
    // Le contrat est simple : on sauvegarde l'événement métier + son vecteur calculé
    void save(MemoryEvent memory, float[] embedding);
}