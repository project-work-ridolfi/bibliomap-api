package it.unipegaso.api.util;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;

public final class SessionIDProvider {

	private SessionIDProvider() {
		// Costruttore privato
	}

	private static final String SESSION_COOKIE_NAME = "SESSION_ID";

	private static final Duration SESSION_DURATION = Duration.ofDays(7);
	private static final int MAX_AGE_SECONDS = (int) TimeUnit.DAYS.toSeconds(SESSION_DURATION.toDays());

	// Legge la Session ID dalla richiesta
	public static Optional<String> getSessionId(HttpHeaders headers) {
		return Optional.ofNullable(headers.getCookies().get(SESSION_COOKIE_NAME))
				.map(c -> c.getValue()); 
	}

	// Crea un nuovo Cookie da inviare al client
	public static NewCookie createSessionCookie(String sessionId, boolean isSecure) {
		return new NewCookie.Builder(SESSION_COOKIE_NAME)
				.value(sessionId)
				.path("/")
				.maxAge(MAX_AGE_SECONDS) 
				.httpOnly(true)
				.secure(isSecure)
				.build();
	}
}