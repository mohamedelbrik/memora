package com.memora.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DateExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DateExtractionService.class);
    private final ChatClient chatClient;

    public DateExtractionService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public DateRange extractDateRange(String query) {
        // 0. HEURISTIQUE SUPRÊME : Date Explicite (YYYY-MM-DD)
        // Si l'utilisateur donne une date précise, on la prend direct.
        Pattern isoDatePattern = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
        Matcher isoMatcher = isoDatePattern.matcher(query);
        if (isoMatcher.find()) {
            String dateStr = isoMatcher.group(1);
            LocalDate date = LocalDate.parse(dateStr);
            log.info("🎯 Direct Date Match found: {}", date);
            return new DateRange(date, date);
        }
        String lowerQuery = query.toLowerCase();
        LocalDate today = LocalDate.now();

        // 1. HEURISTIQUES (L'ordre compte !)

        // D'ABORD les phrases longues
        if (containsWord(lowerQuery, "avant-hier") || containsWord(lowerQuery, "avant hier")) {
            return new DateRange(today.minusDays(2), today.minusDays(2));
        }

        // ENSUITE les mots courts
        if (containsWord(lowerQuery, "hier")) {
            return new DateRange(today.minusDays(1), today.minusDays(1));
        }

        if (containsWord(lowerQuery, "aujourd'hui") || containsWord(lowerQuery, "ce jour")) {
            return new DateRange(today, today);
        }

        // Prompt DURCI avec Exemples Négatifs (Few-Shot)
        String prompt = """
        DATE ACTUELLE : %s
        QUERY UTILISATEUR : "%s"
        
        TACHE : Extrais une plage de date UNIQUEMENT si l'utilisateur mentionne une période temporelle EXPLICITE.
        
        CAS CLASSIQUES (Tu DOIS répondre NULL) :
        - "Combien de souvenirs ?" -> NULL
        - "Quel est le total ?" -> NULL
        - "Parle moi de Kafka" -> NULL
        - "Je veux des statistiques" -> NULL
        - "Qui suis-je ?" -> NULL
        
        CAS TEMPORELS (Tu DOIS répondre START=...;END=...) :
        - "Hier" -> START=...;END=...
        - "En janvier 2024" -> START=2024-01-01;END=2024-01-31
        - "La semaine dernière" -> ...
        
        FORMAT DE RÉPONSE : START=YYYY-MM-DD;END=YYYY-MM-DD ou NULL
        """.formatted(today, query);

        String response = chatClient.prompt()
                .system("Tu es un moteur logique binaire. Tu ne devines jamais. Au moindre doute, c'est NULL.")
                .user(prompt)
                .options(OllamaOptions.builder().withTemperature(0.0).build()) // ZERO Créativité
                .call()
                .content();

        log.info("📆 Date Extractor Response: {}", response);
        return parseResponse(response);
    }

    // Petit helper pour éviter les faux positifs (ex: "hierarchique" contient "hier")
    private boolean containsWord(String text, String word) {
        return java.util.regex.Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private DateRange parseResponse(String response) {
        try {
            if (response.contains("NULL")) return null;
            
            Pattern pattern = Pattern.compile("START=(\\d{4}-\\d{2}-\\d{2});END=(\\d{4}-\\d{2}-\\d{2})");
            Matcher matcher = pattern.matcher(response.trim());
            
            if (matcher.find()) {
                LocalDate start = LocalDate.parse(matcher.group(1));
                LocalDate end = LocalDate.parse(matcher.group(2));
                return new DateRange(start, end);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date range from AI: {}", response);
        }
        return null;
    }

    public record DateRange(LocalDate start, LocalDate end) {}
}