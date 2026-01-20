package it.unipegaso.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrevoContact(
    @JsonProperty("name") String name,
    @JsonProperty("email") String email
) {}
