package it.unipegaso.database;

import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Location;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LocationsRepository {

	private static final Logger LOG = Logger.getLogger(LocationsRepository.class);

	private final String ID = "_id";

	@Inject
	MongoCollection<Location> locations;


	public Optional<Point> getOne(String id) {

		if (id == null || id.trim().isEmpty()) {
			return Optional.empty();
		}


		Location locationModel = locations.find(Filters.eq(ID, id)).first();

		LOG.infof("Query DB per location '%s'. Trovata: %b", locationModel, locationModel != null);

		Point point = locationModel.location;
		// Ritorniamo Optional<Point> con il geoLocation
		return Optional.ofNullable(point);
	}

	/*
	 * inserisce una nuova location nel db
	 * @param newLocation l'oggetto location
	 * @return l'id con la quale e' stata inserita la location nel db
	 * @throws MongoWriteException in caso qualcosa non sia andato bene
	 */
	public String createLocation(Location newLocation) throws MongoWriteException {
	    
	    // Assegna l'ID come Stringa UUID generata da Java
	    newLocation.id = UUID.randomUUID().toString();

	    InsertOneResult result = locations.insertOne(newLocation);

	    // Verifica e logga (l'ID è già nell'oggetto newLocation)
	    if (!result.wasAcknowledged()) {
	        LOG.error("Inserimento location non confermato dal database.");
	        return null;
	    }

	    // L'ID è stato assegnato prima dell'inserimento ed è garantito non nullo.
	    return newLocation.id; 
	}


}
