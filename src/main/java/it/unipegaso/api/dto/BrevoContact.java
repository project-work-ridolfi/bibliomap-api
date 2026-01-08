package it.unipegaso.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BrevoContact(
    @JsonProperty("name") String name,
    @JsonProperty("email") String email
) {}
