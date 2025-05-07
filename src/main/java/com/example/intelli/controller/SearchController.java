package com.example.intelli.controller;

import com.example.intelli.model.SearchRequest;
import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.intelli.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

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
}
