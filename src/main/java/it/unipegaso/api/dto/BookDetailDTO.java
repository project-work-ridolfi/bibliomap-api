package it.unipegaso.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record BookDetailDTO( 
		String id, // id della copia
		String isbn,
		String title,
		String author,
		String coverUrl,
		String customCover,
		Integer publicationYear,
		String language,
		String coverType,
		String publisher,

		// dati libreria e proprietario
		String libraryName,
		String libraryId,
		String ownerId,     // fondamentale per il check isowner nel fe
		String ownerName,   // username per display

		// dati specifici copia
		String condition,
		String status,
		String ownerNotes,
		List<String> tags,
		long views,
		double distance
		) {}


