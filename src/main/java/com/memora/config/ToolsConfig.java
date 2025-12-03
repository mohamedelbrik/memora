package com.memora.config;

import com.memora.application.port.out.MemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class ToolsConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolsConfig.class);

    // 1. Le DTO de requête (Ce que le LLM va remplir automatiquement)
    public record MemoryCountRequest(String keyword) {}

    // 2. L'Outil (Function Bean)
    // La @Description est VITALE : c'est ce que Gemma lit pour savoir QUAND utiliser cet outil.
    @Bean
    @Description("Compte le nombre exact de souvenirs qui contiennent un mot-clé spécifique. Utiliser '*' comme mot-clé pour tout compter.")
    public Function<MemoryCountRequest, Integer> countMemoriesTool(MemoryRepository memoryRepository) {
        return request -> {
            log.info("🤖 AGENT ACTION: Appel de l'outil de comptage pour '{}'", request.keyword());
            return memoryRepository.countMemoriesByKeyword(request.keyword());
        };
    }
}