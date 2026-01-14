package it.unipegaso.api.util;

import java.time.Duration;
import java.time.Instant;
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

    /**
     * Costruisce l'header "Set-Cookie" completo come stringa.
     *
     * @param value Il valore del cookie (Session ID)
     * @param maxAgeSeconds Durata in secondi
     * @param isHttpsRequest True se la richiesta originale era HTTPS
     * @return Stringa pronta per l'header "Set-Cookie"
     */
    public static String buildSetCookieHeader(String value, int maxAgeSeconds, boolean isHttpsRequest) {
        StringBuilder sb = new StringBuilder();

        sb.append(SESSION_COOKIE_NAME).append("=").append(value).append("; ");
        sb.append("Path=/; ");
        sb.append("Max-Age=").append(maxAgeSeconds).append("; ");
        sb.append("HttpOnly; ");
   
        if (isHttpsRequest) {
            sb.append("Secure; ");
            sb.append("SameSite=None; "); 
            sb.append("Partitioned; ");   
        } else {
            sb.append("SameSite=Lax; ");  // Fallback sicuro per sviluppo locale
        }

        return sb.toString();
    }

    // Metodo helper per cookie scaduto (logout)
    public static String buildExpiredSetCookieHeader(boolean isHttpsRequest) {
        return buildSetCookieHeader("", 0, isHttpsRequest);
    }
}
