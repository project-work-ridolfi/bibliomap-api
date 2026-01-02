package it.unipegaso.database;

import java.util.List;
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
		newLocation.setId(UUID.randomUUID().toString());
		InsertOneResult result = locations.insertOne(newLocation);
		if (!result.wasAcknowledged()) {
			LOG.error("Inserimento location non confermato.");
			return null;
		}
		return newLocation.getId();
	}

	@Override
	public Optional<Location> get(String id) {
		if (id == null || id.trim().isEmpty()) return Optional.empty();
		return Optional.ofNullable(locations.find(Filters.eq(ID, id)).first());
	}

	@Override
	public boolean update(Location obj) throws MongoWriteException {
		LOG.error("le location non si aggiornano");
		return false;
	}

	@Override
	public boolean delete(String id) {

		if (id == null || id.trim().isEmpty()) {
			return false;
		}

		DeleteResult result = locations.deleteOne(Filters.eq(ID, id));

		return result.wasAcknowledged();	}

	@Override
	public FindIterable<Location> find(Bson filter) {
		return locations.find(filter);
	}

	@Override
	public long count() {
		return locations.countDocuments();
	}

	public double[] getCoordinates(String locationId) {
		return get(locationId).map(loc -> {
			List<Double> coords = loc.getLocation().getCoordinates().getValues();

			if (coords.size() >= 2) {
				// MongoDB GeoJSON: [0] è Longitudine, [1] è Latitudine
				return new double[]{ coords.get(1), coords.get(0) }; // Restituiamo [lat, lng]
			}
			return null;
		}).orElse(null);
	}


}