package com.memora.application.service;

import com.memora.application.port.in.ChatMemoryUseCase;
import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MemoryChatService implements ChatMemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoryChatService.class);
    
    private final MemoryRepository memoryRepository;
    private final ChatClient chatClient;

    public MemoryChatService(MemoryRepository memoryRepository, ChatClient.Builder chatClientBuilder) {
        this.memoryRepository = memoryRepository;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(String query, String userId, String userName) {
        //List<Memory> memories = memoryRepository.findSimilar(query, 5, 0.4);
        List<Memory> memories = memoryRepository.searchHybrid(query, 5);
        
        String context = "";
        if (memories.isEmpty()) {
            log.info("No relevant memories found for query: {}", query);
        } else {
            // The contents of the found memories are concatenated.
            // On construit un contexte structuré avec des numéros de rang
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
            Tu es Memora, l'assistant personnel intelligent de l'utilisateur : %s.
            
            CONTEXTE (Souvenirs disponibles) :
            %s
            ---------------------
            
            TACHE :
            Fais une synthèse naturelle de la situation pour l'utilisateur en combinant les informations trouvées dans le contexte.
            
            CONSIGNES DE RÉDACTION :
            1. FUSIONNE les informations : Associe la cause technique précise (Source #1) avec le contexte général de la panne (Source #2).
            2. TON : Parle comme un Tech Lead expérimenté qui fait un rapport oral à son équipe. Sois fluide et professionnel.
            3. PRÉCISION : Conserve les termes techniques importants (Error 503, Load Balancer, Infrastructure).
            4. FORMAT : Fais un paragraphe cohérent, pas de liste à puces.
            """.formatted(userName, context);

        // ...
        log.info("--- PROMPT CONTEXT START ---\n{}\n--- PROMPT CONTEXT END ---", context);

        // 3. GENERATION : Call Mistral via Ollama
        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
    }
}