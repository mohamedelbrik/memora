package com.memora.application.service;

import com.memora.adapter.out.persistence.PgVectorMemoryRepository;
import com.memora.application.port.in.ChatMemoryUseCase;
import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MemoryChatService implements ChatMemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoryChatService.class);
    
    private final MemoryRepository memoryRepository;
    private final ChatClient chatClient;
    private final DateExtractionService dateExtractionService;
    private final QueryRewriterService queryRewriterService; // Injecte-le
    private final ChatMemory chatMemory; // Injecte-le

    public MemoryChatService(MemoryRepository memoryRepository, ChatClient.Builder chatClientBuilder, DateExtractionService dateExtractionService, QueryRewriterService queryRewriterService, ChatMemory chatMemory) {
        this.memoryRepository = memoryRepository;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
        this.dateExtractionService = dateExtractionService;
        this.queryRewriterService = queryRewriterService;
        this.chatMemory = chatMemory;
    }

    @Override
    public String chat(String query, String userId, String userName) {
        List<Memory> memories = memoryRepository.searchHybrid(query, 5,null);
        
        String context = "";
        if (memories.isEmpty()) {
            log.info("No relevant memories found for query: {}", query);
        } else {
            // The contents of the found memories are concatenated.
            // We construct a structured context with rank numbers
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < memories.size(); i++) {
                Memory mem = memories.get(i);
                contextBuilder.append(String.format("SOURCE #%d (Score %.2f) : %s\n\n",
                        i + 1, mem.relevanceScore(), mem.content()));
            }
            context = contextBuilder.toString();
        }

        // Prompt Engineering
        // We give Memora a personality and inject the context into it.
        String systemPrompt = """
            Tu es Memora, l'assistant personnel de l'utilisateur : %s.
            
            CONTEXTE (Souvenirs récupérés) :
            %s
            ---------------------
            
            CONSIGNES DE RÉPONSE :
            1. FOCUS : Réponds UNIQUEMENT à la question posée. Ne raconte pas ta vie.
            2. FILTRAGE : Le contexte contient peut-être des informations inutiles (bruit). Ignore les souvenirs qui n'ont aucun rapport sémantique avec la question.
            3. EXEMPLE : Si on te demande un nom, donne juste le nom et le contexte direct. Ne parle pas de serveurs ou de météo si ce n'est pas le sujet.
            4. SYNTHÈSE : Si plusieurs souvenirs répondent à la question, combine-les intelligemment.
            5. IDENTITÉ : Adresse-toi à l'utilisateur ("Tu").
            
            Si la réponse n'est pas dans le contexte, dis-le simplement.
            """.formatted(userName, context);

        // ...
        log.info("--- PROMPT CONTEXT START ---\n{}\n--- PROMPT CONTEXT END ---", context);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
    }

    @Override
    public Flux<String> chatStream(String query, String userId, String userName) {

        // 1. RÉÉCRITURE (Cerveau 1)
        // On transforme "Pourquoi ?" en "Pourquoi y a-t-il eu une panne ?"
        String searchQuery = queryRewriterService.rewriteQuery(query, userId);

        // 2. ANALYSE INTENTION & DATE (Cerveau 2)
        // On analyse la query réécrite (plus précise)
        DateExtractionService.DateRange dateRange = dateExtractionService.extractDateRange(searchQuery);
        List<Memory> memories;

        // 3. ROUTAGE INTELLIGENT (Strategy Pattern)
        if (dateRange != null) {
            log.info("📅 MODE TIMELINE activé pour : {}", dateRange.start());
            // Mode Journal : On veut tout ce qui s'est passé ce jour-là
            memories = memoryRepository.findByDateRange(dateRange);
        } else {
            log.info("🔍 MODE RECHERCHE activé pour : {}", searchQuery);
            // Mode RRF : On cherche par pertinence avec la query RÉÉCRITE
            // CORRECTION MAJEURE ICI : utilisation de searchQuery au lieu de query
            memories = memoryRepository.searchHybrid(searchQuery, 5, null);
        }

        // 4. COUPE-CIRCUIT
        if (memories.isEmpty()) {
            return Flux.just("Désolé " + userName + ", je n'ai trouvé aucun souvenir correspondant.");
        }

        // 5. CONSTRUCTION DU CONTEXTE (Avec étiquettes temporelles)
        StringBuilder contextBuilder = new StringBuilder();
        LocalDate today = LocalDate.now(); // Date pivot pour le calcul

        for (int i = 0; i < memories.size(); i++) {
            Memory mem = memories.get(i);

            // Calcul "Hier / Avant-hier"
            String timeLabel = "Date inconnue";
            if (mem.metadata() != null && mem.metadata().containsKey("ingested_at")) {
                String rawDate = mem.metadata().get("ingested_at").toString();
                if (rawDate.length() >= 10) {
                    LocalDate memDate = LocalDate.parse(rawDate.substring(0, 10));
                    long daysDiff = ChronoUnit.DAYS.between(memDate, today);

                    if (daysDiff == 0) timeLabel = "AUJOURD'HUI";
                    else if (daysDiff == 1) timeLabel = "HIER";
                    else if (daysDiff == 2) timeLabel = "AVANT-HIER";
                    else timeLabel = "LE " + memDate;
                }
            }

            contextBuilder.append(String.format("SOURCE #%d [%s] (Score %.2f) : %s\n\n",
                    i + 1, timeLabel, mem.relevanceScore(), mem.content()));
        }
        String context = contextBuilder.toString();

        // Date lisible pour le prompt
        String todayDateStr = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH));

        // 6. PROMPT ENGINEERING (Le Cerveau Final)
        // CORRECTION : Ajout du placeholder %s pour la date
        String systemPrompt = """
            Tu es Memora, l'assistant personnel de %s.
            NOUS SOMMES LE : %s
            
            CONTEXTE (Souvenirs) :
            %s
            ---------------------
            
            CONSIGNES :
            1. CHRONOLOGIE : Fie-toi EXCLUSIVEMENT à l'étiquette temporelle [HIER], [AVANT-HIER].
            2. FILTRE STRICT : Si l'utilisateur demande "Hier", ignore les sources marquées [AVANT-HIER].
            3. STYLE : Direct, factuel et naturel. Pas de formules de politesse robotiques.
            """.formatted(userName, todayDateStr, context);

        // 7. GENERATION
        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                // --- AJOUT ICI ---
                .functions("countMemoriesTool") // Le nom du Bean défini dans ToolsConfig
                // -----------------
                .advisors(a -> a
                        .param("chat_memory_conversation_id", userId)
                        .param("chat_memory_response_size", 10)
                )
                .stream()
                .content()
                .doOnNext(System.out::print);
    }
}