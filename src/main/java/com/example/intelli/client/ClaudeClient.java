package com.example.intelli.client;

import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import com.example.intelli.service.AwsSecretsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

/**
 * Client for interacting with Claude API using the Messages API.
 */
@Component
public class ClaudeClient {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private String claudeApiKey;
    private final String claudeModel; // Instance field for model from properties

    // Constants used in API calls
    private static final int MAX_TOKENS = 2048;
    private static final String ANTHROPIC_VERSION_HEADER_VALUE = "2023-06-01";
    private static final String MAIN_SECRET_NAME = "prod/AppBeta/IntelliSnap";
    private static final String CLAUDE_API_KEY_JSON_KEY = "claude-api-key";

    // New DTOs for Claude Messages API
    static class Message {
        private String role;
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    static class ClaudeRequest {
        private String model;
        @JsonProperty("max_tokens")
        private int maxTokens;
        private List<Message> messages;
        // private double temperature; // Could add if needed

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public List<Message> getMessages() { return messages; }
        public void setMessages(List<Message> messages) { this.messages = messages; }
    }

    private final AwsSecretsService awsSecretsService;

    public ClaudeClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AwsSecretsService awsSecretsService,
                        @Value("${claude.api.url}") String claudeApiUrl,
                        @Value("${claude.model}") String claudeModelName) { // claudeModelName from properties
        this.webClient = webClientBuilder.baseUrl(claudeApiUrl).build();
        this.objectMapper = objectMapper;
        this.awsSecretsService = awsSecretsService;
        this.claudeModel = claudeModelName; // Use model from properties
    }

    @PostConstruct
    public void init() {
        try {
            this.claudeApiKey = awsSecretsService.getSecretValueFromJson(MAIN_SECRET_NAME, CLAUDE_API_KEY_JSON_KEY);
            if (this.claudeApiKey == null || this.claudeApiKey.isEmpty()) {
                logger.error("Claude API key is null or empty after fetching from Secrets Manager.");
                throw new IllegalStateException("Claude API key could not be initialized.");
            }
            logger.info("Claude API key loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize Claude API key from AWS Secrets Manager: {}", e.getMessage(), e);
            // Application might not be able to function correctly without the API key.
            // Depending on policy, you might re-throw or handle gracefully.
            throw new RuntimeException("Failed to initialize Claude API key", e);
        }
    }

    /**
     * Calls Claude API to get a technology explanation, use cases, and code examples.
     */
    public TechResponse getTechResponse(String inputText, String language) {
        String prompt = "Explain the following technology concept in simple terms, provide 2 use cases, and 2 code examples.\n" +
                "Concept: " + inputText + "\n" +
                "Language: " + language + "\n" +
                "Respond in JSON with fields: explanation (string), useCases (array of String), code (array of String).";
        return callClaudeApiForResponse(prompt, TechResponse.class);
    }

    /**
     * Calls Claude API to get a language definition and example sentences.
     */
    public LanguageResponse getLanguageResponse(String inputText, String language) {
        String prompt = "Define the following word or phrase and give 2 example sentences.\n" +
                "Word/Phrase: " + inputText + "\n" +
                "Language: " + language + "\n" +
                "Respond in JSON with fields: definition (string), exampleSentences (array of String).";
        return callClaudeApiForResponse(prompt, LanguageResponse.class);
    }

    private <T> T callClaudeApiForResponse(String prompt, Class<T> responseClass) {
        String claudeGeneratedJson = callClaudeApi(prompt);
        String jsonToParse = claudeGeneratedJson; 

        try {
            if (jsonToParse != null && !jsonToParse.isEmpty()) {
                logger.info("Attempting to strip markdown from Claude response. Original string: '{}'", jsonToParse);
                String processedJson = jsonToParse.trim();

                if (processedJson.startsWith("```")) {
                    int firstBrace = processedJson.indexOf('{');
                    if (firstBrace != -1) {
                        int lastBrace = processedJson.lastIndexOf('}');
                        if (lastBrace > firstBrace) {
                            processedJson = processedJson.substring(firstBrace, lastBrace + 1);
                            logger.info("Stripped markdown by finding first '{{' and last '}}'. Result for parsing: '{}'", processedJson);
                        } else {
                            String tempJson = processedJson.substring(firstBrace);
                            if (tempJson.endsWith("```")) {
                                tempJson = tempJson.substring(0, tempJson.length() - 3).trim();
                            }
                            logger.warn("Found '{{' but not a valid subsequent '}}' in markdown-fenced content. Attempting to parse from '{{' to end (after removing optional trailing '```'). Original: '{}', Attempted substring: '{}'", jsonToParse, tempJson);
                            processedJson = tempJson;
                        }
                    } else {
                        String tempJson = processedJson;
                        if (tempJson.toLowerCase().startsWith("```json")) {
                            int jsonKeywordEnd = tempJson.toLowerCase().indexOf("json") + 4;
                            tempJson = tempJson.substring(jsonKeywordEnd).trim(); 
                        } else {
                             tempJson = tempJson.substring(3).trim();
                        }
                        if (tempJson.endsWith("```")) {
                            tempJson = tempJson.substring(0, tempJson.length() - 3).trim();
                        }
                        logger.warn("Starts with '```' but no '{{' found. Attempted to remove '```json' and '```' fences. Original: '{}', Result for parsing: '{}'", jsonToParse, tempJson);
                        processedJson = tempJson; 
                    }
                }
                jsonToParse = processedJson;
            }

            if (jsonToParse == null || jsonToParse.isEmpty()) {
                logger.error("Cannot parse null or empty JSON string. Raw response from Claude was null, empty, or became so after processing.");
                throw new IllegalArgumentException("Cannot parse null or empty JSON string from Claude API response.");
            }
            
            logger.info("Attempting to parse with Jackson: '{}'", jsonToParse);
            return objectMapper.readValue(jsonToParse, responseClass);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Claude-generated JSON. Raw response from Claude: '{}'. String attempted for parsing: '{}'. Error: {}",
                    claudeGeneratedJson, jsonToParse, e.getMessage(), e);
            throw new RuntimeException("Failed to parse Claude-generated JSON response", e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during Claude API response processing. Raw response from Claude: '{}'. String attempted for parsing: '{}'. Error: {}",
                    claudeGeneratedJson, jsonToParse, e.getMessage(), e);
            throw new RuntimeException("Unexpected error processing Claude API response", e);
        }
    }

    private String callClaudeApi(String userPrompt) {
        if (this.claudeApiKey == null || this.claudeApiKey.isEmpty()) {
            logger.error("Claude API key is not available at the time of calling the API.");
            throw new IllegalStateException("Claude API key is not configured.");
        }

        ClaudeRequest claudeRequest = new ClaudeRequest();
        claudeRequest.setModel(this.claudeModel); // Use instance field 'claudeModel' from properties
        claudeRequest.setMaxTokens(MAX_TOKENS); 
        Message userMessage = new Message();
        userMessage.setRole("user");
        userMessage.setContent(userPrompt);
        claudeRequest.setMessages(Collections.singletonList(userMessage));

        try {
            String requestBody = objectMapper.writeValueAsString(claudeRequest);
            logger.info("Claude API Request Body: {}", requestBody);

            Mono<String> responseMono = webClient.post()
                    .uri("/v1/messages") // Relative URI to base claudeApiUrl
                    .header("x-api-key", claudeApiKey) 
                    .header("anthropic-version", ANTHROPIC_VERSION_HEADER_VALUE) 
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                logger.error("Error from Claude API. Status: {}. Body: {}", clientResponse.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("Claude API Error - Status: " + clientResponse.statusCode() + ", Body: " + errorBody));
                            })
                    )
                    .bodyToMono(String.class)
                    .doOnError(error -> logger.error("Error during Claude API call (WebClient reactive chain): {}", error.getMessage(), error));
            
            String rawResponse = responseMono.block(); 

            if (rawResponse == null || rawResponse.isEmpty()) {
                logger.error("Received null or empty response string from Claude API after webClient call.");
                return "{\"error\":\"Received null or empty response from Claude API\"}";
            }
            logger.info("Raw response string from Claude API: {}", rawResponse);

            JsonNode rootNode = objectMapper.readTree(rawResponse);
            
            JsonNode contentArrayNode = rootNode.path("content");
            if (!contentArrayNode.isArray() || contentArrayNode.isEmpty()) {
                logger.error("Claude API response 'content' is not an array or is empty. Full response: {}", rawResponse);
                return "{\"error\":\"Claude API response 'content' is not an array or is empty\"}";
            }

            JsonNode firstContentObject = contentArrayNode.get(0);
            if (firstContentObject == null || !firstContentObject.has("type") || !firstContentObject.path("type").asText().equals("text") || !firstContentObject.has("text")) {
                 logger.error("First content object in Claude API response is not of type 'text' or missing 'text' field. Content object: {}. Full response: {}", firstContentObject != null ? firstContentObject.toString() : "null", rawResponse);
                 return "{\"error\":\"Invalid content structure in Claude API response\"}";
            }
            
            String textContent = firstContentObject.path("text").asText();
            logger.info("Extracted text content (this will be 'claudeGeneratedJson'): {}", textContent);
            return textContent;

        } catch (JsonProcessingException e) {
            logger.error("Error serializing Claude request or deserializing raw API response: {}", e.getMessage(), e);
            return "{\"error\":\"JSON processing error during Claude API call\"}";
        } catch (Exception e) { 
            logger.error("Unexpected error calling Claude API or processing its response: {}", e.getMessage(), e);
            return "{\"error\":\"Unexpected error during Claude API call\"}";
        }
    }
}
