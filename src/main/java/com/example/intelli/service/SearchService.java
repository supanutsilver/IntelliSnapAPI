package com.example.intelli.service;

import org.springframework.http.codec.ServerSentEvent;

import com.example.intelli.client.ClaudeClient;
import com.example.intelli.client.VectorDbClient;
import com.example.intelli.model.SearchRequest;
import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class SearchService {
    private final VectorDbClient vectorDbClient;
    private final ClaudeClient claudeClient;

    @Autowired
    public SearchService(VectorDbClient vectorDbClient, ClaudeClient claudeClient) {
        this.vectorDbClient = vectorDbClient;
        this.claudeClient = claudeClient;
    }

    public TechResponse handleTechMode(SearchRequest request) {
        // 1. Check vector DB for similar inputText
        TechResponse cached = vectorDbClient.findSimilarTech(request.getInputText(), request.getLanguage(), 0.85f);
        if (cached != null) {
            return cached;
        }
        // 2. Call Claude API
        TechResponse response = claudeClient.getTechResponse(request.getInputText(), request.getLanguage());
        // 3. Save to vector DB (async)
        vectorDbClient.saveTechAsync(request.getInputText(), request.getLanguage(), response);
        return response;
    }

    public LanguageResponse handleLanguageMode(SearchRequest request) {
        // 1. Check vector DB for similar inputText
        LanguageResponse cached = vectorDbClient.findSimilarLanguage(request.getInputText(), request.getLanguage(), 0.85f);
        if (cached != null) {
            return cached;
        }
        // 2. Call Claude API
        LanguageResponse response = claudeClient.getLanguageResponse(request.getInputText(), request.getLanguage());
        // 3. Save to vector DB (async)
        vectorDbClient.saveLanguageAsync(request.getInputText(), request.getLanguage(), response);
        return response;
    }
    
    public Flux<ServerSentEvent<String>> chatBotClaudeSse(SearchRequest request) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SearchService.class);
        String prompt = "Explain the following technology concept in simple terms, provide 2 main use cases, and 2 code examples.\n\n" + request.getInputText();
        logger.info("[streamClaudeSse] Received streaming request for inputText: {}", request.getInputText());
        logger.info("[streamClaudeSse] Sending prompt to Claude: {}", prompt);
        return claudeClient.streamClaudeSse(prompt)
            .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
            .map(chunk -> ServerSentEvent.builder(chunk).build())
            .doOnNext(chunk -> logger.info("[streamClaudeSse] Received chunk from Claude: {}", chunk))
            .doOnError(err -> logger.error("[streamClaudeSse] Error in Claude stream: {}", err.getMessage(), err))
            .doOnComplete(() -> logger.info("[streamClaudeSse] Streaming complete."));
    }
}
