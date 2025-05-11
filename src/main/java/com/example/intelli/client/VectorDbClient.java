package com.example.intelli.client;

import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for interacting with Qdrant vector database for caching and searching responses.
 */
@Component
public class VectorDbClient {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VectorDbClient.class);
    private static final String COLLECTION = "intelli_cache";
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final OpenAiClient openAiClient;

    public VectorDbClient(ObjectMapper objectMapper,
                          WebClient.Builder webClientBuilder,
                          @Value("${vectordb.url:http://localhost:6333}") String vectorDbUrl,
                          OpenAiClient openAiClient) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl(vectorDbUrl).build();
        this.openAiClient = openAiClient;
    }

    /**
     * Search for a similar tech response in the vector DB.
     */
    public TechResponse findSimilarTech(String inputText, String language, float threshold) {
        return findSimilar(inputText, language, threshold, "tech", TechResponse.class);
    }

    /**
     * Save a tech response to the vector DB.
     */
    public void saveTech(String inputText, String language, TechResponse response) {
        save("tech", inputText, language, response);
    }

    @Async
    public void saveTechAsync(String inputText, String language, TechResponse response) {
        try {
            saveTech(inputText, language, response);
        } catch (Exception e) {
            logger.error("Async saveTech failed", e);
        }
    }

    /**
     * Search for a similar language response in the vector DB.
     */
    public LanguageResponse findSimilarLanguage(String inputText, String language, float threshold) {
        return findSimilar(inputText, language, threshold, "language", LanguageResponse.class);
    }

    /**
     * Save a language response to the vector DB.
     */
    public void saveLanguage(String inputText, String language, LanguageResponse response) {
        save("language", inputText, language, response);
    }

    @org.springframework.scheduling.annotation.Async
    public void saveLanguageAsync(String inputText, String language, LanguageResponse response) {
        try {
            saveLanguage(inputText, language, response);
        } catch (Exception e) {
            logger.error("Async saveLanguage failed", e);
        }
    }

    private <T> T findSimilar(String inputText, String language, float threshold, String mode, Class<T> clazz) {
        float[] embedding = openAiClient.getEmbedding(inputText);
        String searchBody = String.format("{\"vector\": %s, \"top\": 1, \"score_threshold\": %f, \"with_payload\": true}",
                toJsonArray(embedding), threshold);
        String resp = webClient.post()
                .uri("/collections/" + COLLECTION + "/points/search")
                .bodyValue(searchBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(resp);
            if (root.has("result") && root.get("result").isArray() && root.get("result").size() > 0) {
                JsonNode payload = root.get("result").get(0).get("payload");
                if (payload != null && payload.has("mode") && payload.get("mode").asText().equals(mode)) {
                    return objectMapper.readValue(payload.get("response").toString(), clazz);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Qdrant search/parse error", e);
        }
        return null;
    }

    private <T> void save(String mode, String inputText, String language, T response) {
        float[] embedding = openAiClient.getEmbedding(inputText);
        String payload;
        try {
            payload = String.format("{\"mode\":\"%s\",\"inputText\":%s,\"language\":%s,\"response\":%s}",
                    mode,
                    objectMapper.writeValueAsString(inputText),
                    objectMapper.writeValueAsString(language),
                    objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
        String upsertBody = String.format("{\"points\": [{\"id\": %d, \"vector\": %s, \"payload\": %s}]}",
                System.nanoTime(), toJsonArray(embedding), payload);
        webClient.put()
                .uri("/collections/" + COLLECTION + "/points?wait=true")
                .bodyValue(upsertBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String toJsonArray(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

