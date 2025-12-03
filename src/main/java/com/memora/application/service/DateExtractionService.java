package com.memora.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

        // Prompt spécialisé "Extraction de Date"
        String prompt = """
            DATE ACTUELLE : %s
            QUERY UTILISATEUR : "%s"
            
            TACHE : Analyse la query. Si elle contient une référence temporelle (ex: "hier", "avant-hier", "lundi dernier", "en janvier"), calcule la plage de dates correspondante.
            
            RÈGLES :
            1. "Avant-hier" = Date actuelle - 2 jours.
            2. "Hier" = Date actuelle - 1 jour.
            3. Si aucune référence temporelle n'est trouvée, réponds "NULL".
            
            FORMAT DE RÉPONSE ATTENDU (Strictement) :
            START=YYYY-MM-DD;END=YYYY-MM-DD
            
            Exemple de réponse : START=2025-11-26;END=2025-11-26
            """.formatted(today, query);

        String response = chatClient.prompt()
                .system("Tu es un extracteur de dates. Tu ne parles pas, tu ne dis pas bonjour. Tu sors uniquement le format demandé.")
                .user(prompt)
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