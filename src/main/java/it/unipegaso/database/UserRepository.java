package it.unipegaso.database;

import java.util.Optional;

import org.bson.Document;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserRepository {

	private static final Logger LOG = Logger.getLogger(UserRepository.class);

	private final String USERNAME = "username";
	private final String EMAIL = "email";
	
    @Inject
    MongoCollection<Document> users; 

    /**
     * Trova un utente tramite l'username.
     * @param username 
     * @return Il Documento MongoDB dell'utente, se trovato; altrimenti Optional.empty().
     */
    public Optional<Document> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        // Creazione del filtro: cerca un Documento dove il campo "username" 
        // è esattamente uguale al valore fornito.
        Document userDoc = users.find(Filters.eq(USERNAME, username)).first();

        LOG.infof("Query DB per username '%s'. Trovato: %b", username, userDoc != null);

        // Ritorniamo un Optional per gestire in modo pulito il caso "non trovato" (null).
        return Optional.ofNullable(userDoc);
    }
    
    /**
     * Trova un utente tramite l'email.
     * @param email L'email da cercare.
     * @return Il Documento MongoDB dell'utente, se trovato; altrimenti Optional.empty().
     */
    public Optional<Document> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }

        Document userDoc = users.find(Filters.eq(EMAIL, email)).first();

        LOG.infof("Query DB per email '%s'. Trovato: %b", email, userDoc != null);

        // Ritorniamo un Optional per gestire in modo pulito il caso "non trovato" (null).
        return Optional.ofNullable(userDoc);
    }
}
