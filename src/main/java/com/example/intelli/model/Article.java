package com.example.intelli.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Document(collection = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    @Id
    private String id;
    private String title;
    private String url;
    private String image_url;
    private String summary;
}

