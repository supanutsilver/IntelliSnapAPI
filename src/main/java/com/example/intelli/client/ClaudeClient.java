package com.example.intelli.client;

import com.example.intelli.model.TechResponse;
import com.example.intelli.model.LanguageResponse;
import com.example.intelli.service.AwsSecretsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.intelli.model.ClaudeMessage;
import com.example.intelli.model.ClaudeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with Claude API using the Messages API.
 */
@Component
public class ClaudeClient {
    // ... existing fields and methods
    public String chatBotClaude(String message) {
        return callClaudeApi(message);
    }
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
        ClaudeMessage userMessage = new ClaudeMessage();
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

    public Flux<String> streamClaudeSse(String prompt) {
        if (this.claudeApiKey == null) {
            init(); // Ensure API key is loaded
        }
        if (this.claudeApiKey == null) {
            logger.error("Claude API key is not initialized after init(). Cannot make streaming call.");
            return Flux.error(new IllegalStateException("Claude API key not initialized."));
        }

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", this.claudeModel);
        requestBody.put("max_tokens", MAX_TOKENS);
        requestBody.put("stream", true);
        requestBody.put("messages", Collections.singletonList(message));

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing stream request body: {}", e.getMessage(), e);
            return Flux.error(e);
        }
        
        logger.info("[ClaudeClient] Sending streaming request to Claude /v1/messages");
        logger.info("[ClaudeClient] Request body: {}", requestBodyJson);
        logger.info("[ClaudeClient] Headers: x-api-key=***, anthropic-version={}, anthropic-beta=messages-2023-12-15, Content-Type=application/json, Accept=text/event-stream", ANTHROPIC_VERSION_HEADER_VALUE);

        return this.webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", this.claudeApiKey)
                .header("anthropic-version", ANTHROPIC_VERSION_HEADER_VALUE)
                .header("anthropic-beta", "messages-2023-12-15")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .bodyValue(requestBodyJson)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(jsonDataLine -> logger.info("[ClaudeClient] Raw SSE line: {}", jsonDataLine))
                .flatMap(jsonDataLine -> { 
                    if (jsonDataLine.trim().isEmpty()) { 
                        return Flux.empty();
                    }
                    if ("[DONE]".equals(jsonDataLine.trim())) {
                        logger.info("[ClaudeClient] Received [DONE] marker, stream ending.");
                        return Flux.empty(); 
                    }

                    try {
                        JsonNode eventNode = objectMapper.readTree(jsonDataLine);
                        String eventType = eventNode.path("type").asText();

                        if ("content_block_delta".equals(eventType)) {
                            JsonNode deltaNode = eventNode.path("delta");
                            if ("text_delta".equals(deltaNode.path("type").asText())) {
                                String textChunk = deltaNode.path("text").asText();
                                if (textChunk != null && !textChunk.isEmpty()) {
                                    logger.debug("[ClaudeClient] Extracted text_delta: '{}'", textChunk);
                                    return Flux.just(textChunk);
                                }
                            }
                        } else if ("error".equals(eventType)) {
                            String errorType = eventNode.path("error").path("type").asText("unknown_error");
                            String errorMessage = eventNode.path("error").path("message").asText("Unknown streaming error");
                            logger.error("[ClaudeClient] Claude API streaming error event: Type: {}, Message: {}", errorType, errorMessage);
                            return Flux.error(new RuntimeException("Claude API error (" + errorType + "): " + errorMessage));
                        } else {
                            logger.debug("[ClaudeClient] Received SSE event of type '{}', not a text_delta.", eventType);
                        }
                        return Flux.empty(); 
                    } catch (JsonProcessingException e) {
                        logger.warn("[ClaudeClient] Error parsing supposed JSON line: '{}'. Raw line: '{}'", e.getMessage(), jsonDataLine);
                        return Flux.empty(); 
                    }
                })
                .doOnError(e -> logger.error("[ClaudeClient] Error in Claude SSE processing pipeline: {}", e.getMessage(), e))
                .doOnComplete(() -> logger.info("[ClaudeClient] Claude SSE Flux processing completed."));
    }
}
