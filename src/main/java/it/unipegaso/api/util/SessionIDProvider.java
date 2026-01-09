package it.unipegaso.api.util;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;

public final class SessionIDProvider {

	private SessionIDProvider() { }

	private static final String SESSION_COOKIE_NAME = "SESSION_ID";
	private static final Duration DEFAULT_SESSION_DURATION = Duration.ofDays(7); 
	private static final int DEFAULT_MAX_AGE_SECONDS = (int) TimeUnit.DAYS.toSeconds(DEFAULT_SESSION_DURATION.toDays());

	// legge la Session ID dalla richiesta
	public static Optional<String> getSessionId(HttpHeaders headers) {
		return Optional.ofNullable(headers.getCookies().get(SESSION_COOKIE_NAME))
				.map(c -> c.getValue()); 
	}

	// crea un Cookie a BREVE durata (usato per l'OTP)
	public static NewCookie createSessionCookie(String sessionId, boolean isSecure) {
		return new NewCookie.Builder(SESSION_COOKIE_NAME)
				.value(sessionId)
				.path("/")
				.maxAge(DEFAULT_MAX_AGE_SECONDS) 
				.httpOnly(true)
				.secure(isSecure)
				.sameSite(NewCookie.SameSite.NONE)
				.build();
	}


	// crea il cookie di sessione a LUNGA durata (per l'autenticazione).
	public static NewCookie createAuthenticatedSessionCookie(String authenticatedSessionId, boolean isSecure, int maxAgeSeconds) {

		Instant expiryInstant = Instant.now().plusSeconds(maxAgeSeconds);

		return new NewCookie.Builder(SESSION_COOKIE_NAME)
				.value(authenticatedSessionId)
				.path("/")
				.maxAge(maxAgeSeconds)
				.expiry(Date.from(expiryInstant)) 
				.secure(isSecure) 
				.httpOnly(true)
				.sameSite(NewCookie.SameSite.NONE)
				.build();

	}


	// Crea un cookie scaduto per forzare il browser a cancellare la sessione.
	public static NewCookie createExpiredSessionCookie(String sessionId) {
		
		Instant expiredInstant = Instant.now().minus(1, ChronoUnit.HOURS);     	
		return new NewCookie.Builder(SESSION_COOKIE_NAME)
				.value("")
				.path("/")
				.maxAge(0) // Forza la cancellazione
				.expiry(Date.from(expiredInstant)) // Scaduto nel passato
				.secure(false) 
				.httpOnly(true)
				.sameSite(NewCookie.SameSite.LAX)
				.build();
	}
}
