package it.unipegaso.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrevoRequest(
    @JsonProperty("sender") BrevoContact sender,
    @JsonProperty("to") List<BrevoContact> to,
    @JsonProperty("subject") String subject,
    @JsonProperty("htmlContent") String htmlContent,
    @JsonProperty("attachment") List<BrevoAttachment> attachment
) {}
