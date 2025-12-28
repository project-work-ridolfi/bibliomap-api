package it.unipegaso.database.model;

import java.util.Arrays;
import java.util.Optional;

public enum VisibilityOptions {
	ALL,
	LOGGED_IN,
	PRIVATE;

	public static Optional<VisibilityOptions> fromString(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}

		return Arrays.stream(values())
				.filter(v -> v.name().equalsIgnoreCase(value))
				.findFirst();
	}

	public String toDbValue() {
		return name().toLowerCase();
	}
}
