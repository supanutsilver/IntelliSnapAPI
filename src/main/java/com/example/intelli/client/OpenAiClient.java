package com.example.intelli.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.intelli.service.AwsSecretsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OpenAiClient {
    private static final String OPENAI_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String MAIN_SECRET_NAME = "prod/AppBeta/IntelliSnap";
    private static final String OPENAI_API_KEY_JSON_KEY = "openai-api-key";

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private String openAiApiKey;

    public OpenAiClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder, AwsSecretsService secretsService) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com/v1/embeddings").build();
        this.openAiApiKey = secretsService.getSecretValueFromJson(MAIN_SECRET_NAME, OPENAI_API_KEY_JSON_KEY);

        if (this.openAiApiKey == null || this.openAiApiKey.isEmpty()) {
            logger.error("OpenAI API key not found or empty in AWS Secrets Manager (key: '{}' in secret: '{}').", 
                         OPENAI_API_KEY_JSON_KEY, MAIN_SECRET_NAME);
            // Depending on how critical OpenAI is, you might throw an exception:
            // throw new IllegalStateException("OpenAI API key could not be initialized.");
        } else {
            logger.info("OpenAI API key initialized successfully from AWS Secrets Manager.");
        }
    }

    public float[] getEmbedding(String inputText) {
        try {
            if (openAiApiKey == null || openAiApiKey.isEmpty()) { 
                logger.error("OpenAI API key is not configured. Cannot get embedding.");
                throw new IllegalStateException("OpenAI API key not set or failed to initialize.");
            }
            String requestBody = String.format("{\"input\": %s, \"model\": \"%s\"}",
                    objectMapper.writeValueAsString(inputText), OPENAI_EMBEDDING_MODEL);
            String resp = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode arr = objectMapper.readTree(resp).get("data").get(0).get("embedding");
            float[] result = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) result[i] = (float) arr.get(i).asDouble();
            return result;
        } catch (Exception e) {
            logger.error("Failed to get embedding", e);
            throw new RuntimeException("Failed to get embedding", e);
        }
    }
}
