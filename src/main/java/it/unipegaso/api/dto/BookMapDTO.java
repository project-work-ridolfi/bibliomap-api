package it.unipegaso.api.dto;

import java.util.List;

public record BookMapDTO(
    String id,          // ID della Copia
    String title,       // Titolo Libro
    String author,      // Autore
    String libraryName, // Nome Libreria
    String libraryId,   // Id Libreria
    String status,      // Status del libro
    long views,         // Numero visualizzazioni
    double lat,         // Latitudine
    double lng,         // Longitudine
    double distance,    // Distanza in km
    boolean isFuzzed,   // Flag privacy
    String cover,       // Url della cover
    String customCover, // Cover in b64 della copia
    String ownerId,     // Proprietario della copia
    String username,    // Nome da mostrare
    List<String> tags	// Lista dei tag
) {}