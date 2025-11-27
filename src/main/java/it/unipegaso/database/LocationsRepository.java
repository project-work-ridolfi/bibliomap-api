package it.unipegaso.database;

import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Location;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LocationsRepository implements IRepository<Location> {

    private static final Logger LOG = Logger.getLogger(LocationsRepository.class);
    private static final String ID = "_id"; 

    @Inject
    MongoCollection<Location> locations; 
    

    @Override
    public String create(Location newLocation) throws MongoWriteException {
        newLocation.id = UUID.randomUUID().toString();
        InsertOneResult result = locations.insertOne(newLocation);
        if (!result.wasAcknowledged()) {
            LOG.error("Inserimento location non confermato.");
            return null;
        }
        return newLocation.id;
    }

    @Override
    public Optional<Location> get(String id) {
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(locations.find(Filters.eq(ID, id)).first());
    }

	@Override
	public boolean update(Location obj) throws MongoWriteException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean delete(String id) {
		// TODO Auto-generated method stub
		return false;
	}

    
}