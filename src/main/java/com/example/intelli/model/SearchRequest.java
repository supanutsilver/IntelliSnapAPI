package com.example.intelli.model;




public class SearchRequest {
    private String inputText;
    private String mode;
    private String language = "en";

    public SearchRequest() {}

    public String getInputText() {
        return inputText;
    }
    public void setInputText(String inputText) {
        this.inputText = inputText;
    }
    public String getMode() {
        return mode;
    }
    public void setMode(String mode) {
        this.mode = mode;
    }
    public String getLanguage() {
        return language == null ? "en" : language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
}
