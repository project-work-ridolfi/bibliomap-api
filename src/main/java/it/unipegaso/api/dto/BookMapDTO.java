package it.unipegaso.api.dto;

import java.util.List;

public record BookMapDTO(
    String id,          // ID della Copia
    String title,       // Titolo Libro
    String author,      // Autore
    String libraryName, // Nome Libreria
    String libraryId,
    String status,
    long views,
    double lat,         // Latitudine
    double lng,         // Longitudine
    double distance,    // Distanza in km
    boolean isFuzzed,   // Flag privacy
    String cover,       // Base 64 della cover
    String ownerId,     // Proprietario della copia
    String username,    // Nome da mostrare
    List<String> tags	// Lista dei tag
) {}