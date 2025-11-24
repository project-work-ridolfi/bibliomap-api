package it.unipegaso.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.database.LocationsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BookService {

	
    private static final Logger LOG = Logger.getLogger(BookService.class);

	@Inject
	LocationsRepository locationsRepository;

	@Inject
	MongoClient mongoClient; 

	public List<BookMapDTO> searchBooks(double lat, double lng, double radiusKm, String visibilityFilter, String excludeUserId, String searchText, String sortBy) {

		MongoCollection<Document> locationsCol = mongoClient.getDatabase("bibliomap").getCollection("locations");

		List<Bson> pipeline = new ArrayList<>();

		// 1. GEOSPAZIALE ($geoNear)
		Document geoNear = new Document("$geoNear", new Document()
				.append("near", new Document("type", "Point").append("coordinates", Arrays.asList(lng, lat)))
				.append("distanceField", "distance")
				.append("maxDistance", radiusKm * 1000) 
				.append("spherical", true)
				);
		pipeline.add(geoNear);

		// 2. JOIN LIBRERIE ($lookup + $unwind)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "libraries")
				.append("localField", "_id")
				.append("foreignField", "locationId") 
				.append("as", "library")
				));
		pipeline.add(new Document("$unwind", "$library"));

		// 3, JOIN UTENTI ($lookup + $unwind)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "users")           
				.append("localField", "library.ownerId") 
				.append("foreignField", "_id")     
				.append("as", "ownerInfo")
				));

		pipeline.add(new Document("$unwind", new Document("path", "$ownerInfo").append("preserveNullAndEmptyArrays", true)));


		// 4. FILTRI PRELIMINARI SULLA LIBRERIA ($match)
		List<Bson> libraryFilters = new ArrayList<>();

		// Filtro visibilità
		List<String> allowedVisibilities = new ArrayList<>(Arrays.asList("all"));
		if ("logged-in".equals(visibilityFilter)) allowedVisibilities.add("logged-in");
		libraryFilters.add(Filters.in("library.visibility", allowedVisibilities));

		// Filtro Exclude User (Se presente)
		if (excludeUserId != null && !excludeUserId.isEmpty()) {
			libraryFilters.add(Filters.ne("library.ownerId", excludeUserId));
		}

		// Applica i filtri
		pipeline.add(Aggregates.match(Filters.and(libraryFilters)));


		// 5. JOIN COPIE ($lookup + $unwind)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "copies")
				.append("localField", "library._id") 
				.append("foreignField", "libraryId")
				.append("as", "copy")
				));
		pipeline.add(new Document("$unwind", "$copy"));


		// 6. JOIN LIBRI ($lookup + $unwind)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "books")
				.append("localField", "copy.book_isbn") 
				.append("foreignField", "_id")
				.append("as", "bookInfo")
				));
		pipeline.add(new Document("$unwind", "$bookInfo"));


		// 7. FILTRO TESTUALE ($match su titolo/autore)
		if (searchText != null && !searchText.trim().isEmpty()) {
			Pattern regex = Pattern.compile(Pattern.quote(searchText), Pattern.CASE_INSENSITIVE);
			pipeline.add(Aggregates.match(Filters.or(
					Filters.regex("bookInfo.title", regex),
					Filters.regex("bookInfo.author", regex)
					)));
		}

		// 8. ORDINAMENTO ($sort)
		if ("title".equals(sortBy)) {
			pipeline.add(Aggregates.sort(Sorts.ascending("bookInfo.title")));
		} else if ("author".equals(sortBy)) {
			pipeline.add(Aggregates.sort(Sorts.ascending("bookInfo.author")));
		} else {
			// Default: distance 
			pipeline.add(Aggregates.sort(Sorts.ascending("distance")));
		}

		// 9. ESECUZIONE E MAPPING
		List<BookMapDTO> results = new ArrayList<>();
		Random rand = new Random();

		for (Document doc : locationsCol.aggregate(pipeline)) {
			try {
				// Spacchettamento dei sotto-documenti
				Document lib = doc.get("library", Document.class);
				Document copy = doc.get("copy", Document.class);
				Document book = doc.get("bookInfo", Document.class);
				Document geo = doc.get("geolocation", Document.class); 
				Document ownerInfo = doc.get("ownerInfo", Document.class); // Nuovo

				List<Double> coords = geo.getList("coordinates", Double.class);

				// Gestione Privacy (Blur)
				double realLng = coords.get(0);
				double realLat = coords.get(1);
				int blurRadius = lib.getInteger("blurRadius", 0); 
				double finalLat = realLat;
				double finalLng = realLng;
				boolean isFuzzed = false;

				if (blurRadius > 0) {
					double offset = (double) blurRadius / 111000.0; 
					finalLat += (rand.nextDouble() * 2 - 1) * offset;
					finalLng += (rand.nextDouble() * 2 - 1) * offset;
					isFuzzed = true;
				}

				// Gestione Cover Base64
				String rawCover = book.getString("cover"); 
				String coverData = null;
				if (rawCover != null && !rawCover.trim().isEmpty()) {
					coverData = rawCover; 
				}

				// Gestione Username Owner
				String ownerUsername = "Utente Bibliomap"; // Default
				if (ownerInfo != null) {
					// Recupera username (o name, o email, dipende dal tuo DB)
					String extractedName = ownerInfo.getString("username");
					if (extractedName != null) ownerUsername = extractedName;
				}

				// Creazione DTO
				results.add(new BookMapDTO(
						copy.getString("_id"),
						book.getString("title"),
						book.getString("author"),
						lib.getString("name"),
						finalLat,
						finalLng,
						doc.getDouble("distance") / 1000.0, // Distanza in Km
						isFuzzed,
						coverData,
						lib.getString("ownerId"),
						ownerUsername)); 

			} catch (Exception e) {
				LOG.error("Errore mapping libro: " + e.getMessage());
				LOG.debug(e.getStackTrace());
			}
		}

		return results;
	}
}