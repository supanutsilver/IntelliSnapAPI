package com.example.intelli.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AwsSecretsService {

    private static final Logger logger = LoggerFactory.getLogger(AwsSecretsService.class);
    
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AwsSecretsService(SecretsManagerClient secretsManagerClient, ObjectMapper objectMapper) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves a secret value from AWS Secrets Manager
     * 
     * @param secretName The name or ARN of the secret to retrieve
     * @return The secret value as a string
     * @throws RuntimeException if the secret cannot be retrieved
     */
    public String getSecret(String secretName) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretString = response.secretString();
            if (secretString == null) {
                logger.warn("Secret string for '{}' is null.", secretName);
            }
            return secretString;
            
        } catch (SecretsManagerException e) {
            logger.error("Error retrieving secret: {}", secretName, e);
            return null; 
        }
    }

    /**
     * Retrieves a specific value from a JSON secret string stored in AWS Secrets Manager.
     *
     * @param secretName The name or ARN of the secret (which stores a JSON string).
     * @param jsonKey The key of the value to extract from the JSON secret.
     * @return The value associated with the jsonKey, or null if not found or an error occurs.
     */
    public String getSecretValueFromJson(String secretName, String jsonKey) {
        String secretJsonString = getSecret(secretName);
        if (secretJsonString == null) {
            logger.warn("Could not retrieve secret JSON string for secretName: '{}'", secretName);
            return null;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(secretJsonString);
            JsonNode valueNode = rootNode.get(jsonKey);
            if (valueNode == null || valueNode.isNull()) {
                logger.warn("Key '{}' not found or is null in secret '{}'.", jsonKey, secretName);
                return null;
            }
            return valueNode.asText();
        } catch (JsonProcessingException e) {
            logger.error("Error parsing JSON for secret '{}' and key '{}'. JSON: {}", secretName, jsonKey, secretJsonString, e);
            return null;
        }
    }
}
