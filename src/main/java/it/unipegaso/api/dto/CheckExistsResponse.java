package it.unipegaso.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CheckExistsResponse(
	    @JsonProperty("exists") boolean exists
	) {}
