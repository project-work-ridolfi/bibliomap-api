package it.unipegaso.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.VisibilityOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LibrariesRepository implements IRepository<Library> {


	private static final Logger LOG = Logger.getLogger(LibrariesRepository.class);

	private static final String OWNER_ID = "ownerId";


	@Inject
	MongoCollection<Library> libraries;

	@Override
	public String create(Library newLibrary) throws MongoWriteException {

		// assegna l'ID come Stringa UUID generata da Java
		newLibrary.setId(UUID.randomUUID().toString());

		InsertOneResult result = libraries.insertOne(newLibrary);

		// Verifica e logga 
		if (!result.wasAcknowledged()) {
			LOG.error("Inserimento location non confermato dal database.");
			return null;
		}

		// L'ID è stato assegnato prima dell'inserimento ed è garantito non nullo.
		return newLibrary.getId(); 
	}


	@Override
	public Optional<Library> get(String id) {

		if(id == null || id.trim().isEmpty()) {
			LOG.error("ID VUOTO");
			return Optional.empty();
		}

		return Optional.ofNullable(libraries.find(Filters.eq(ID, id)).first());
	}


	public List<String> getVisibleLibraryIds(boolean logged, String userId) {
	    Bson filter;
	    
	    if (logged) {
	        filter = Filters.or(
	            Filters.eq(VISIBILITY, VisibilityOptions.ALL.toDbValue()),
	            Filters.eq(VISIBILITY, VisibilityOptions.LOGGED_IN.toDbValue()),
	            Filters.eq(OWNER_ID, userId)
	        );
	    } else {
	        filter = Filters.eq(VISIBILITY, VisibilityOptions.ALL.toDbValue());
	    }
	    
	    List<String> ids = new ArrayList<>();
	    
	    libraries.find(filter).projection(Projections.include(ID))
	    .forEach(lib -> {
	        ids.add(lib.getId());
	    });
	    return ids;
	}

	public FindIterable<Library> getAll(String userId){

		if(userId == null || userId.trim().isEmpty()) {
			LOG.error("USERID VUOTO");
			return null;
		}

		return libraries.find(Filters.eq(OWNER_ID, userId));
	}


	@Override
	public boolean update(Library obj) throws MongoWriteException {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public boolean delete(String id) {
		DeleteResult result = libraries.deleteOne(Filters.eq(ID, id));

		if (result.wasAcknowledged()) {
			if (result.getDeletedCount() > 0) {
				LOG.infof("Utente con '%s' '%s' eliminato con successo.", ID, id);
				return true;
			} else {
				LOG.warnf("Nessun utente trovato con '%s' '%s'.", ID, id);
				//ritorno sempre true perche' comunque l'utente non esiste piu' che e' il risultato desiderato
				return true;
			}
		} else {
			LOG.errorf("Eliminazione dell'utente con '%s' '%s' non riconosciuta dal database.",ID, id);
			return false;
		}
	}


	@Override
	public FindIterable<Library> find(Bson filter) {
		return libraries.find(filter);
	}
	
	@Override
	public long count() {
		return libraries.countDocuments();
	}
	
}
