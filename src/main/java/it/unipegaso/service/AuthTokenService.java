package it.unipegaso.service;

import io.smallrye.jwt.build.Jwt;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

@ApplicationScoped
public class AuthTokenService {

    @Inject
    @ConfigProperty(name = "jwt.issuer", defaultValue = "https://default-issuer.dev")
    String ISSUER;
    
    /**
     * Genera un token JWT per l'utente appena registrato.
     * @param user L'oggetto User appena creato.
     * @return Il token JWT come stringa.
     */
    public String generateJwt(User user) {
        
        // Definisce il momento di scadenza (es. 2 ore da ora)
        long expirationTime = Instant.now().plus(2, ChronoUnit.HOURS).getEpochSecond();
        
        // Costruisce e firma il token
        return Jwt.issuer(ISSUER)
                .groups(Collections.singleton("user")) // Ruolo base
                .subject(user.id) // ID utente come Subject
                .expiresAt(expirationTime)
                .sign(); // Firma il token
    }
    
}
