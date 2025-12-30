package it.unipegaso.database;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Aggregates.unwind;
import static com.mongodb.client.model.Sorts.descending;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
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

	public long countByLibraryIds(List<String> libraryIds) {

		if (libraryIds == null || libraryIds.isEmpty()) {
			return 0;
		}

		// conta i documenti che hanno libraryId contenuto nella lista fornita
		return copies.countDocuments(Filters.in("libraryId", libraryIds));
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


	@Override
	public long count() {
		return copies.countDocuments();
	}

	public String findTopTagByLibraryIds (List<String> userLibIds) {
		// Se l'utente non ha librerie, inutile procedere
		if (userLibIds == null || userLibIds.isEmpty()) {
			return "nessuno";
		}

		// esegue l'aggregazione filtrando per i libraryId ottenuti
		List<Bson> pipeline = Arrays.asList(
				// Filtra le copie che appartengono a una delle librerie dell'utente
				match(Filters.in("libraryId", userLibIds)),

				// Separa l'array dei tags per poterli contare singolarmente
				unwind("$tags"),

				// Raggruppa per nome del tag e conta le occorrenze
				group("$tags", sum("count", 1)),

				// Ordina dal più frequente
				sort(descending("count")),

				// Prende solo il primo
				limit(1)
				);

		Document res = copies.withDocumentClass(Document.class)
				.aggregate(pipeline)
				.first();

		LOG.debug("TOP TAG RESULT: " + res);  

		// In MongoDB aggregate, l'ID del raggruppamento (il nome del tag) finisce nel campo "_id"
		return res != null ? res.getString("_id") : "nessuno";
	}

	public Map<String, Long> getTags(List<String> userLibIds) {
		
	    Map<String, Long> tagsMap = new LinkedHashMap<>();

	    if (userLibIds == null || userLibIds.isEmpty()) {
	        return tagsMap;
	    }

	    List<Bson> pipeline = Arrays.asList(
	            match(Filters.in("libraryId", userLibIds)),
	            unwind("$tags"),
	            group("$tags", sum("count", 1)),
	            sort(descending("count"))
	    );

	    copies.withDocumentClass(Document.class)
	            .aggregate(pipeline)
	            .forEach(doc -> {
	                String tagName = doc.getString("_id");
	                Long count = doc.getInteger("count").longValue();
	                tagsMap.put(tagName, count);
	            });

	    return tagsMap;
	}

}