package com.example.intelli.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechResponse {
    private String explanation;
    private List<String> useCases;
    private List<String> code;
}
