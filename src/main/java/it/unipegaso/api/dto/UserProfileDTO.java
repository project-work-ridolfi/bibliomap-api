package it.unipegaso.api.dto;

import java.util.List;
import java.util.Map;

public record UserProfileDTO(
		String userId,
		String userName,
		Map<String,Long> tags,
		String visibility,
		Map<String, Double> coords,
		int blurRadius,
		List<String> libraryIds) {
}
