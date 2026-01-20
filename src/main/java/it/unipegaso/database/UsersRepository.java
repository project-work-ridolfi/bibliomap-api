package it.unipegaso.database;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import it.unipegaso.database.model.User;
import it.unipegaso.database.model.VisibilityOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UsersRepository implements IRepository<User>{

	private static final Logger LOG = Logger.getLogger(UsersRepository.class);

	public static final String USERNAME = "username";
	public static final String EMAIL = "email";

	@Inject
	MongoCollection<User> users; // Iniettiamo MongoCollection<User>


	public Optional<User> findByUsername(String username) {
		if (username == null || username.trim().isEmpty()) {
			return Optional.empty();
		}

		Bson filter = Filters.eq(USERNAME, username);
		User userModel = find(filter).first();

		LOG.infof("Query DB per username '%s'. Trovato: %b", username, userModel != null);

		return Optional.ofNullable(userModel);
	}

	public Optional<User> findByEmail(String email) {
		if (email == null || email.trim().isEmpty()) {
			return Optional.empty();
		}

		User userModel = find(Filters.eq(EMAIL, email)).first();

		LOG.infof("Query DB per email '%s'. Trovato: %b", email, userModel != null);

		return Optional.ofNullable(userModel);
	}

	public String create(User newUser) {
		try {

			String id = UUID.randomUUID().toString();

			if (newUser.getId() == null) {
				newUser.setId(id);
			}else {
				id = newUser.getId();
			}
			Date now = new Date();
			newUser.setCreatedAt(now);
			newUser.setModifiedAt(now);

			InsertOneResult result = users.insertOne(newUser);

			// Controlla se l'inserimento ha avuto successo (acknowledge) e se ha generato un ID
			if (result.wasAcknowledged() && result.getInsertedId() != null) {
				// l'operazione è considerata un successo.
				return id;
			} else {
				LOG.error("Inserimento utente non confermato dal database.");
				return null; 
			}

		} catch (MongoWriteException e) {
			// Gestione dell'errore di univocita' (11000)
			if (e.getError().getCode() == 11000) {
				throw new IllegalArgumentException("L'username o l'email esistono già.", e);
			}
			// Rilancia tutti gli altri errori DB/runtime
			throw new RuntimeException("Errore DB durante la creazione utente.", e);
		}
	}

	//cancella il primo trovato per coppia chiave valore
	public boolean delete(String key, String value) {
		if (value == null || value.trim().isEmpty()) {
			LOG.warn("Tentativo di eliminazione con un valore nullo o vuoto.");
			return false;
		}

		DeleteResult result = users.deleteOne(Filters.eq(key, value));

		if (result.wasAcknowledged()) {
			if (result.getDeletedCount() > 0) {
				LOG.infof("Utente con '%s' '%s' eliminato con successo.", key, value);
				return true;
			} else {
				LOG.warnf("Nessun utente trovato con '%s' '%s'.", key, value);
				//ritorno sempre true perche' comunque l'utente non esiste piu' che e' il risultato desiderato
				return true;
			}
		} else {
			LOG.errorf("Eliminazione dell'utente con '%s' '%s' non riconosciuta dal database.", key, value);
			return false;
		}
	}


	@Override
	public Optional<User> get(String id) {

		if(id == null || id.trim().isEmpty()) {
			LOG.error("ID VUOTO");
			return Optional.empty();
		}

		return Optional.ofNullable(users.find(Filters.eq(ID, id)).first());
	}

	@Override
	public boolean update(User user) throws MongoWriteException {

		if (user.getId() == null) {
			LOG.error("Aggiornamento utente fallito: ID utente mancante.");
			return false;
		}

		// Aggiorna il timestamp di modifica prima della sostituzione
		user.setModifiedAt( new Date());

		String id = user.getId();

		try {
			Bson query = Filters.eq(ID, id); 
			UpdateResult result = users.replaceOne(query, user);

			if (result.wasAcknowledged() && result.getModifiedCount() > 0) {
				return true;
			} else if (result.getModifiedCount() == 0) {
				LOG.warnf("Aggiornamento utente non eseguito: Utente con ID %s non trovato.", id);
				return false;
			} else {
				LOG.errorf("Aggiornamento utente fallito per ID %s.", id);
				return false;
			}
		} catch (MongoWriteException e) {
			LOG.errorf(e, "Errore DB durante l'aggiornamento dell'utente %s.", id);
			throw new RuntimeException("Errore DB durante l'aggiornamento utente.", e);
		}
	}

	@Override
	public boolean delete(String id) {
		return delete(ID, id);
	}

	@Override
	public FindIterable<User> find(Bson filter) {
		return users.find(filter);
	}

	@Override
	public long count() {
		return users.countDocuments();
	}

	public long count(String userID, boolean logged) {

		Bson filter;

		if (logged) {
			filter = Filters.or(
					Filters.eq(VISIBILITY, VisibilityOptions.ALL.toDbValue()),
					Filters.eq(VISIBILITY, VisibilityOptions.LOGGED_IN.toDbValue()),
		            Filters.eq(ID, userID)

					);
		} else {
			filter = Filters.eq(VISIBILITY, VisibilityOptions.ALL.toDbValue());

		}

		return users.countDocuments(filter);

	}



}