package com.example.intelli.controller;

import com.example.intelli.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/intelli")
public class ArticleController {
    private final ArticleService articleService;

    @Autowired
    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/articles")
    public List<Map<String, String>> getAllArticles() {
        return articleService.getAllArticles();
    }

    @PostMapping("/articles/cache/revoke")
    public void revokeArticlesCache() {
        articleService.evictArticlesCache();
    }
}

