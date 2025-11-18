package it.unipegaso.api.dto;

public record RegistrationDTO(
 String email, 
 String username,
 String password, 
 boolean acceptTerms,
 boolean acceptPrivacy 
) {}

