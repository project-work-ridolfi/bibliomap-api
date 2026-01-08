package it.unipegaso.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BrevoAttachment(
    @JsonProperty("content") String content, 
    @JsonProperty("name") String name
) {}
