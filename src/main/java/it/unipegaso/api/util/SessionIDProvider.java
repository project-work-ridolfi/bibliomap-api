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

	// Legge la Session ID dalla richiesta
	public static Optional<String> getSessionId(HttpHeaders headers) {
		return Optional.ofNullable(headers.getCookies().get(SESSION_COOKIE_NAME))
				.map(c -> c.getValue());
	}

	// Cookie a BREVE durata (usato per l'OTP)
	public static NewCookie createSessionCookie(String sessionId, boolean isSecure) {
		return buildCookie(sessionId, DEFAULT_MAX_AGE_SECONDS, isSecure);
	}

	// Cookie di sessione a LUNGA durata (per l'autenticazione)
	public static NewCookie createAuthenticatedSessionCookie(String authenticatedSessionId, boolean isSecure, int maxAgeSeconds) {
		return buildCookie(authenticatedSessionId, maxAgeSeconds, isSecure);
	}

	// Cookie scaduto per forzare il browser a cancellare la sessione
	public static NewCookie createExpiredSessionCookie(boolean isSecure) {
		Instant expiredInstant = Instant.now().minus(1, ChronoUnit.HOURS);

		return new NewCookie.Builder(SESSION_COOKIE_NAME)
				.value("")
				.path("/")
				.maxAge(0)
				.expiry(Date.from(expiredInstant))
				.secure(isSecure)
				.httpOnly(true)
				.sameSite(isSecure ? NewCookie.SameSite.NONE : NewCookie.SameSite.LAX)
				.build();
	}

	// Metodo privato per costruire il cookie con logica condizionale
	private static NewCookie buildCookie(String value, int maxAgeSeconds, boolean isSecure) {
	    Instant expiryInstant = Instant.now().plusSeconds(maxAgeSeconds);

	    return new NewCookie.Builder(SESSION_COOKIE_NAME)
	            .value(value)
	            .path("/")
	            .maxAge(maxAgeSeconds)
	            .expiry(Date.from(expiryInstant))
	            .httpOnly(true)
	            .secure(isSecure)
	            .sameSite(isSecure ? NewCookie.SameSite.NONE : NewCookie.SameSite.LAX)
	            .build();
	}
	
	/**
	 * Genera l'header Set-Cookie completo con supporto per Partitioned.
	 * Da usare quando serve il flag Partitioned per cookie cross-site.
	 */
	public static String buildSetCookieHeader(String value, int maxAgeSeconds, boolean isSecure, boolean partitioned) {
		StringBuilder sb = new StringBuilder();

		sb.append(SESSION_COOKIE_NAME).append("=").append(value).append("; ");
		sb.append("Path=/; ");
		sb.append("Max-Age=").append(maxAgeSeconds).append("; ");
		sb.append("HttpOnly; ");

		if (isSecure) {
			sb.append("Secure; ");
			sb.append("SameSite=None; ");

			if (partitioned) {
				sb.append("Partitioned; ");
			}
		} else {
			sb.append("SameSite=Lax; ");
		}

		return sb.toString();
	}
}