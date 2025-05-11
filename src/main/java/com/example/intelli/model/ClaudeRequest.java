package com.example.intelli.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ClaudeRequest {
    private String model;
    @JsonProperty("max_tokens")
    private int maxTokens;
    private List<ClaudeMessage> messages;
    // private double temperature; // Uncomment if needed
}
