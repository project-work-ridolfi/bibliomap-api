package it.unipegaso.database;

import java.time.LocalDateTime;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UsersRepository {

    private static final Logger LOG = Logger.getLogger(UsersRepository.class);

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
        	
        	LocalDateTime now = LocalDateTime.now();
            newUser.createdAt = now;
            newUser.modifiedAt = now;
            
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
    
    
    /**
     * Aggiorna un utente già esistente
     * @param user L'oggetto User con i campi da aggiornare (locationId, visibility, blurRadius).
     * Deve contenere l'ID valido dell'utente.
     * @return true se l'utente è stato modificato con successo.
     */
    
    public boolean updateUser(User user) {
        if (user.id == null) {
            LOG.error("Aggiornamento utente fallito: ID utente mancante.");
            return false;
        }
        
        // Aggiorna il timestamp di modifica prima della sostituzione
        user.modifiedAt = LocalDateTime.now();

        try {
            Bson query = Filters.eq("_id", user.id); 
            UpdateResult result = users.replaceOne(query, user);
            
            if (result.wasAcknowledged() && result.getModifiedCount() > 0) {
                return true;
            } else if (result.getModifiedCount() == 0) {
                LOG.warnf("Aggiornamento utente non eseguito: Utente con ID %s non trovato.", user.id);
                return false;
            } else {
                LOG.errorf("Aggiornamento utente fallito per ID %s.", user.id);
                return false;
            }
        } catch (MongoWriteException e) {
            LOG.errorf(e, "Errore DB durante l'aggiornamento dell'utente %s.", user.id);
            throw new RuntimeException("Errore DB durante l'aggiornamento utente.", e);
        }
    }
}