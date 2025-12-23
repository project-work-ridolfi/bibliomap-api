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
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import it.unipegaso.database.model.Copy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CopiesRepository implements IRepository<Copy> {

	private static final Logger LOG = Logger.getLogger(CopiesRepository.class);

	@Inject
	MongoCollection<Copy> copies;
	
	private static final String LIBRARY_ID = "libraryId";

	@Override
	public String create(Copy copy) throws MongoWriteException {
		copy.setId( UUID.randomUUID().toString()) ;

		InsertOneResult result = copies.insertOne(copy);

		if (!result.wasAcknowledged()) {
			LOG.error("Inserimento copia non confermato");
			return null;
		}
		return copy.getId();
	}

	@Override
	public Optional<Copy> get(String id) {
		if (id == null || id.trim().isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(copies.find(Filters.eq(ID, id)).first());
	}

	public List<Copy> findByLibrary(String libraryId) {

		Bson filter = Filters.eq(LIBRARY_ID, libraryId);

		return find(filter).into(new ArrayList<>());
	}

	public boolean delete(String id) {

		if (id == null || id.trim().isEmpty()) {
			return false;
		}

		DeleteResult result = copies.deleteOne(Filters.eq(ID, id));

		return result.wasAcknowledged();
	}

	public boolean deleteByLibraryId(String libraryId) {

		if (libraryId == null || libraryId.trim().isEmpty()) {
			return false;
		}

		DeleteResult result = copies.deleteOne(Filters.eq(LIBRARY_ID, libraryId));

		return result.wasAcknowledged();
	}
	
	@Override
	public boolean update(Copy copy) throws MongoWriteException {

		if (copy == null || copy.getId().isEmpty()) {
			return false;
		}
		UpdateResult result = copies.replaceOne(Filters.eq(ID, copy.getId()), copy);

		return result.getMatchedCount() == 1;
	}

	@Override
	public FindIterable<Copy> find(Bson filter) {
		return copies.find(filter);
	}
}