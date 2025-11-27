package it.unipegaso.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Copy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CopiesRepository implements IRepository<Copy> {

    private static final Logger LOG = Logger.getLogger(CopiesRepository.class);

    @Inject
    MongoCollection<Copy> copies;

    @Override
    public String create(Copy copy) throws MongoWriteException {
        copy.id = UUID.randomUUID().toString();
        
        InsertOneResult result = copies.insertOne(copy);
        
        if (!result.wasAcknowledged()) {
            LOG.error("Inserimento copia non confermato");
            return null;
        }
        return copy.id;
    }

    @Override
    public Optional<Copy> get(String id) {
        if (id == null || id.trim().isEmpty()) {
        	return Optional.empty();
        }
        return Optional.ofNullable(copies.find(Filters.eq("_id", id)).first());
    }

    public List<Copy> findByLibrary(String libraryId) {
        return copies.find(Filters.eq("libraryId", libraryId)).into(new ArrayList<>());
    }
    
    public boolean delete(String id) {
    	
        if (id == null || id.trim().isEmpty()) {
        	return false;
        }
        
    	DeleteResult result = copies.deleteOne(Filters.eq("_id", id));
    	
    	return result.wasAcknowledged();
    	
    }
}