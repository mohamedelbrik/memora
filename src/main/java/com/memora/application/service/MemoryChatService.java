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
        List<Memory> memories = memoryRepository.findSimilar(query, 5, 0.4);
        
        String context = "";
        if (memories.isEmpty()) {
            log.info("No relevant memories found for query: {}", query);
        } else {
            // The contents of the found memories are concatenated.
            context = memories.stream()
                .map(Memory::content)
                .collect(Collectors.joining("\n---\n"));
        }

        // Prompt Engineering
        // We give Memora a personality and inject the context into it.
        String systemPrompt = """
                Tu es Memora, l'assistant personnel de l'utilisateur : %s.
                
                    CONTEXTE (Souvenirs de %s) :
                    %s
                    ---------------------
                
                    CONSIGNES STRICTES :
                    1. IDENTITÉ : Tu t'adresses directement à l'utilisateur ("Tu").
                    2. QUALITÉ DE LANGUE : Tu es un expert littéraire. Ton français doit être IRRÉPROCHABLE (accords, conjugaison, syntaxe). Relis-toi.
                    3. PRÉCISION : Utilise le contexte pour répondre. Si une info manque, dis-le.
                    4. STYLE : Sois concis, professionnel et naturel. Évite les répétitions.
                """.formatted(userName, userName, context, userName);

        // 3. GENERATION : Call Mistral via Ollama
        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
    }
}