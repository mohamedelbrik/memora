package com.memora.application.service;

import com.memora.application.port.in.ChatMemoryUseCase;
import com.memora.application.port.out.MemoryRepository;
import com.memora.domain.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

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
        List<Memory> memories = memoryRepository.searchHybrid(query, 5);
        
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
}