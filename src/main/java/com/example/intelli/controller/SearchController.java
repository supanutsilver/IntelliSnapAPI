package com.example.intelli.controller;

import org.springframework.http.codec.ServerSentEvent;

import com.example.intelli.model.SearchRequest;
import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.intelli.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;

import java.time.Duration;

@RestController
@RequestMapping("/intelli")
public class SearchController {
    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(
            @Valid @RequestBody SearchRequest request) {
        if ("tech".equalsIgnoreCase(request.getMode())) {
            TechResponse response = searchService.handleTechMode(request);
            return ResponseEntity.ok(response);
        } else if ("language".equalsIgnoreCase(request.getMode())) {
            LanguageResponse response = searchService.handleLanguageMode(request);
            return ResponseEntity.ok(response);
        } else {
            log.warn("Invalid mode received: {}", request.getMode());
            return ResponseEntity.badRequest().body("Invalid mode");
        }
    }

    @PostMapping(value = "/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamSearch(@RequestBody SearchRequest request) {
        return searchService.searchContextClaudeSse(request);
    }

//    @PostMapping("/chatbot")
//    public ResponseEntity<?> chatBotClaude(@RequestBody Map<String, String> payload) {
//        String message = payload.get("message");
//        if (message == null || message.isBlank()) {
//            return ResponseEntity.badRequest().body("Message cannot be empty");
//        }
//        String reply = searchService.chatBotClaude(message);
//        return ResponseEntity.ok(Map.of("reply", reply));
//    } // Only one instance of this method should exist.

    @PostMapping(value = "/chatbot/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatBotClaudeStream(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        if (message == null || message.isBlank()) {
            return Flux.error(new IllegalArgumentException("Message cannot be empty"));
        }
        return searchService.chatBotClaudeStream(message);
    }

}
