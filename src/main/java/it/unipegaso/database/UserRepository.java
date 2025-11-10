package it.unipegaso.database;

import java.util.Optional;

import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserRepository {

    private static final Logger LOG = Logger.getLogger(UserRepository.class);

    private final String USERNAME = "username";
    private final String EMAIL = "email";
    
    @Inject
    MongoCollection<User> users; // Iniettiamo MongoCollection<User>


    /**
     * Trova un utente tramite l'username.
     * @return Il modello User, se trovato; altrimenti Optional.empty().
     */
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        User userModel = users.find(Filters.eq(USERNAME, username)).first();

        LOG.infof("Query DB per username '%s'. Trovato: %b", username, userModel != null);

        // Ritorniamo Optional<User>
        return Optional.ofNullable(userModel);
    }
    
    /**
     * Trova un utente tramite l'email.
     * @return Il modello User, se trovato; altrimenti Optional.empty().
     */
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }

        User userModel = users.find(Filters.eq(EMAIL, email)).first();

        LOG.infof("Query DB per email '%s'. Trovato: %b", email, userModel != null);

        return Optional.ofNullable(userModel);
    }
    
    /**
     * Salva il nuovo utente nel database.
     * @param newUser L'oggetto User mappato e hashato.
     * @return true se l'utente è stato creato con successo.
     * @throws IllegalArgumentException se fallisce un vincolo di univocità (username/email).
     */
    public boolean createUser(User newUser) {
        try {
            InsertOneResult result = users.insertOne(newUser);

            // Controlla se l'inserimento ha avuto successo (acknowledge) e se ha generato un ID
            if (result.wasAcknowledged() && result.getInsertedId() != null) {
                // l'operazione è considerata un successo.
                return true;
            } else {
                LOG.error("Inserimento utente non confermato dal database.");
                return false; 
            }

        } catch (MongoWriteException e) {
            // Gestione dell'errore di univocità (11000)
            if (e.getError().getCode() == 11000) {
                throw new IllegalArgumentException("L'username o l'email esistono già.", e);
            }
            // Rilancia tutti gli altri errori DB/runtime
            throw new RuntimeException("Errore DB durante la creazione utente.", e);
        }
    }
}