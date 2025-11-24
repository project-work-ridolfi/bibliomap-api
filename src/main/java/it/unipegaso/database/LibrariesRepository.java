package it.unipegaso.database;

import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Library;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LibrariesRepository implements IRepository<Library> {

	
	private static final Logger LOG = Logger.getLogger(LibrariesRepository.class);

	private static final String OWNER_ID = "ownerId";

	
	@Inject
	MongoCollection<Library> libraries;
	
	/*
	 * inserisce una nuova location nel db
	 * @param newLocation l'oggetto location
	 * @return l'id con la quale e' stata inserita la location nel db
	 * @throws MongoWriteException in caso qualcosa non sia andato bene
	 */
	public String create(Library newLibrary) throws MongoWriteException {
	    
	    // Assegna l'ID come Stringa UUID generata da Java
		newLibrary.id = UUID.randomUUID().toString();

	    InsertOneResult result = libraries.insertOne(newLibrary);

	    // Verifica e logga (l'ID è già nell'oggetto newLocation)
	    if (!result.wasAcknowledged()) {
	        LOG.error("Inserimento location non confermato dal database.");
	        return null;
	    }

	    // L'ID è stato assegnato prima dell'inserimento ed è garantito non nullo.
	    return newLibrary.id; 
	}
	

	@Override
	public Optional<Library> get(String id) {

		if(id == null || id.trim().isEmpty()) {
			LOG.error("ID VUOTO");
			return Optional.empty();
		}
		
		return Optional.ofNullable(libraries.find(Filters.eq(ID, id)).first());
	}
	
	
	public FindIterable<Library> getAll(String userId){
		
		if(userId == null || userId.trim().isEmpty()) {
			LOG.error("USERID VUOTO");
			return null;
		}
		
		return libraries.find(Filters.eq(OWNER_ID, userId));
	}
}
