package it.unipegaso.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrevoContact(
    @JsonProperty("name") String name,
    @JsonProperty("email") String email
) {}
