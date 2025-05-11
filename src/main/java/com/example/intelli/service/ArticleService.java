package com.example.intelli.service;

import com.example.intelli.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class ArticleService {
    private static final Logger logger = LoggerFactory.getLogger(ArticleService.class);
    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Cacheable(value = "articles", unless = "#result == null || #result.isEmpty()")
    public List<Map<String, String>> getAllArticles() {
        return articleRepository.findAll().stream()
            .map(article -> Map.of(
                "title", article.getTitle(),
                "url", article.getUrl(),
                "image_url", article.getImage_url(),
                "summary", article.getSummary()
            ))
            .collect(Collectors.toList());
    }

    @CacheEvict(value = "articles", allEntries = true)
    public void evictArticlesCache() {
        // This method will clear the articles cache
        logger.info("[evictArticlesCache] Clearing articles cache");
    }
}
