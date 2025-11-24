package it.unipegaso.api.dto;

public record BookMapDTO(
    String id,           // ID della Copia
    String title,        // Titolo Libro
    String author,       // Autore
    String libraryName,  // Nome Libreria
    double lat,          // Latitudine
    double lng,          // Longitudine
    double distance,     // Distanza in km
    boolean isFuzzed     // Flag privacy
) {}