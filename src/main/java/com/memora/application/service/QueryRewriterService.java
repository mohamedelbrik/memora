package com.memora.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryRewriterService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriterService.class);
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public QueryRewriterService(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder.build();
        this.chatMemory = chatMemory;
    }

    public String rewriteQuery(String originalQuery, String userId) {
        List<Message> history = chatMemory.get(userId, 3);
        if (history.isEmpty()) return originalQuery;

        String conversationHistory = history.stream()
                .map(m -> m.getMessageType() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        // On donne la date au Rewriter pour qu'il puisse résoudre "ce jour-là"
        String today = java.time.LocalDate.now().toString();

        String prompt = """
            DATE ACTUELLE : %s
            
            HISTORIQUE :
            %s
            
            QUESTION UTILISATEUR : "%s"
            
            TACHE : Réécris la question utilisateur pour la rendre totalement AUTONOME et EXPLICITE.
            
            RÈGLES CRITIQUES :
            1. RÉSOLUTION TEMPORELLE : Si l'utilisateur dit "ce jour-là", "à ce moment", "et après ?", tu DOIS remplacer ces mots par la DATE ou l'ÉVÉNEMENT précis mentionné dans l'historique.
            2. EXEMPLE : Si l'historique parle du 26 novembre et que l'user demande "et quoi d'autre ?", réécris en "Quoi d'autre le 26 novembre ?".
            3. NE RÉPONDS PAS. Donne juste la phrase réécrite.
            """.formatted(today, conversationHistory, originalQuery);

        String rewritten = chatClient.prompt().user(prompt).call().content();

        log.info("🔄 Query Rewrite: '{}' -> '{}'", originalQuery, rewritten);
        return rewritten;
    }
}