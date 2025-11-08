package it.unipegaso.api.dto;

// Usato nell'endpoint POST /api/auth/register
public record RegistrationFinalDTO(
    String username,
    String email,
    String password,
    boolean acceptTerms
) {}