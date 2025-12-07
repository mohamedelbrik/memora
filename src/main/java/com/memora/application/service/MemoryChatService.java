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

        // 1. EXTRACTION DATE (Rien ne change)
        DateExtractionService.DateRange dateRange = dateExtractionService.extractDateRange(query);

        // 2. RÉÉCRITURE (Rien ne change)
        String searchQuery = queryRewriterService.rewriteQuery(query, userId);

        if (dateRange != null) {
            log.info("📅 TIME TRAVEL : {} -> {}", dateRange.start(), dateRange.end());
        }

        // 3. RECHERCHE (Rien ne change)
        List<Memory> memories;
        if (dateRange != null) {
            // Assure-toi que cette méthode utilise bien le Threshold 0.0 comme vu avant !
            memories = ((PgVectorMemoryRepository) memoryRepository).findByDateRange(dateRange);
        } else {
            // Idem, Threshold 0.0
            memories = ((PgVectorMemoryRepository) memoryRepository).searchHybrid(searchQuery, 5, null);
        }

        // --- 4. MODIFICATION MAJEURE ICI ---
        // ON SUPPRIME LE COUPE-CIRCUIT (if empty return...)
        // On prépare juste le contexte, qu'il soit vide ou plein.

        StringBuilder contextBuilder = new StringBuilder();
        LocalDate today = LocalDate.now();

        if (memories.isEmpty()) {
            contextBuilder.append("AUCUN SOUVENIR TROUVÉ DANS LA BASE DE DONNÉES.");
            log.warn("⚠️ Base de données muette pour cette requête.");
        } else {
            for (int i = 0; i < memories.size(); i++) {
                Memory mem = memories.get(i);
                // ... (ton calcul de timeLabel reste identique) ...
                String timeLabel = "Date inconnue"; // ... ton code ...

                contextBuilder.append(String.format("SOURCE #%d [%s] (Score %.2f) : %s\n\n",
                        i + 1, timeLabel, mem.relevanceScore(), mem.content()));
            }
        }

        String context = contextBuilder.toString();
        String todayDateStr = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH));

        // 5. PROMPT ENGINEERING ADAPTÉ
        // On dit à l'IA : "Si le contexte est vide, utilise tes outils ou dis que tu ne sais pas."
        String systemPrompt = """
            Tu es Memora, l'assistant personnel de %s.
            NOUS SOMMES LE : %s
            
            CONTEXTE RAG (Résultat de la recherche base de données) :
            %s
            ---------------------
            
            CONSIGNES :
            1. Si le CONTEXTE contient des infos, utilise-les pour répondre.
            2. Si le CONTEXTE est vide ou "AUCUN SOUVENIR", tu as deux choix :
               - Si la question porte sur un COMPTAGE ("combien de..."), utilise l'outil 'countMemoriesTool'.
               - Sinon, dis poliment que tu n'as pas l'info dans les souvenirs.
            3. CHRONOLOGIE : Fie-toi aux étiquettes [HIER], [AVANT-HIER].
            """.formatted(userName, todayDateStr, context);

        // 6. APPEL LLM (Toujours exécuté maintenant !)
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                // ✅ C'est là que la magie opère : si le contexte est vide,
                // Llama peut décider d'appeler cet outil tout seul !
                .functions("countMemoriesTool")
                .advisors(a -> a
                        .param("chat_memory_conversation_id", userId)
                        .param("chat_memory_response_size", 10)
                )
                .call()
                .content();

        log.info("🤖 RÉPONSE FINALE AGENT : {}", response);

        // ASTUCE UX : On simule le streaming pour le Frontend !
        // On découpe la phrase par mots (espace) tout en gardant les délimiteurs
        String[] words = response.split("(?<=\\s)");

        return Flux.fromArray(words)
                .delayElements(java.time.Duration.ofMillis(50)) // Petit délai "effet humain"
                .doOnComplete(() -> log.info("✅ Streaming simulé terminé."));
    }
}