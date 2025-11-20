package it.unipegaso.api.dto;

public record LibraryDTO(
    String name,
    String locationType, // user_default o new_location
    String visibility
) {}
