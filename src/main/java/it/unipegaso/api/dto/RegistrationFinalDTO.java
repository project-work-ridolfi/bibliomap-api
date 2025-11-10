package it.unipegaso.api.dto;

public record RegistrationFinalDTO(
    String username,
    String email,
    String password,
    boolean acceptTerms
) {}