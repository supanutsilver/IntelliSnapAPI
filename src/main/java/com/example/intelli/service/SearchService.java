package com.example.intelli.service;

import com.example.intelli.client.ClaudeClient;
import com.example.intelli.client.VectorDbClient;
import com.example.intelli.model.SearchRequest;
import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        // 3. Save to vector DB
        vectorDbClient.saveTech(request.getInputText(), request.getLanguage(), response);
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
        // 3. Save to vector DB
        vectorDbClient.saveLanguage(request.getInputText(), request.getLanguage(), response);
        return response;
    }
}
