package it.unipegaso.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.database.LocationsRepository;
import it.unipegaso.database.model.Book;
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

		// query geospaziale
		Document geoNear = new Document("$geoNear", new Document()
				.append("near", new Document("type", "Point").append("coordinates", Arrays.asList(lng, lat)))
				.append("distanceField", "distance")
				.append("maxDistance", radiusKm * 1000)
				.append("spherical", true)
				);
		pipeline.add(geoNear);

		// join librerie
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "libraries")
				.append("localField", "_id")
				.append("foreignField", "locationId")
				.append("as", "library")
				));
		pipeline.add(new Document("$unwind", "$library"));

		// filtri preliminari su libreria
		List<Bson> libraryFilters = new ArrayList<>();

		List<String> allowedVisibilities = new ArrayList<>(Arrays.asList("all"));
		if ("logged-in".equals(visibilityFilter)) allowedVisibilities.add("logged-in");
		libraryFilters.add(Filters.in("library.visibility", allowedVisibilities));

		if (excludeUserId != null && !excludeUserId.trim().isEmpty()) {
			libraryFilters.add(Filters.ne("library.ownerId", excludeUserId));
		}

		pipeline.add(Aggregates.match(Filters.and(libraryFilters)));

		// join utenti per dati owner
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "users")
				.append("localField", "library.ownerId")
				.append("foreignField", "_id")
				.append("as", "ownerInfo")
				));
		// mantieni la libreria anche se l'utente non esiste più
		pipeline.add(new Document("$unwind", new Document("path", "$ownerInfo").append("preserveNullAndEmptyArrays", true)));

		// join copie
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "copies")
				.append("localField", "library._id")
				.append("foreignField", "libraryId")
				.append("as", "copy")
				));
		pipeline.add(new Document("$unwind", "$copy"));

		// join libri
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "books")
				.append("localField", "copy.book_isbn")
				.append("foreignField", "_id")
				.append("as", "bookInfo")
				));
		pipeline.add(new Document("$unwind", "$bookInfo"));

		// filtro ricerca testuale
		if (searchText != null && !searchText.trim().isEmpty()) {
			Pattern regex = Pattern.compile(Pattern.quote(searchText), Pattern.CASE_INSENSITIVE);
			pipeline.add(Aggregates.match(Filters.or(
					Filters.regex("bookInfo.title", regex),
					Filters.regex("bookInfo.author", regex)
					)));
		}

		// ordinamento risultati
		if ("title".equals(sortBy)) {
			pipeline.add(Aggregates.sort(Sorts.ascending("bookInfo.title")));
		} else if ("author".equals(sortBy)) {
			pipeline.add(Aggregates.sort(Sorts.ascending("bookInfo.author")));
		} else {
			pipeline.add(Aggregates.sort(Sorts.ascending("distance")));
		}

		List<BookMapDTO> results = new ArrayList<>();
		Random rand = new Random();

		for (Document doc : locationsCol.aggregate(pipeline)) {
			try {
				Document lib = doc.get("library", Document.class);
				Document copy = doc.get("copy", Document.class);
				Document book = doc.get("bookInfo", Document.class);
				Document geo = doc.get("geolocation", Document.class);
				Document ownerInfo = doc.get("ownerInfo", Document.class);

				// calcolo blur privacy massimo tra utente e libreria
				int libBlur = lib.getInteger("blurRadius", 0);
				int userBlur = (ownerInfo != null) ? ownerInfo.getInteger("blurRadius", 0) : 0;
				int effectiveBlur = Math.max(libBlur, userBlur);

				// applicazione fuzzing coordinate se necessario
				List<Double> coords = geo.getList("coordinates", Double.class);
				double finalLat = coords.get(1);
				double finalLng = coords.get(0);
				boolean isFuzzed = false;

				if (effectiveBlur > 0) {
					double offset = (double) effectiveBlur / 111000.0; 
					finalLat += (rand.nextDouble() * 2 - 1) * offset;
					finalLng += (rand.nextDouble() * 2 - 1) * offset;
					isFuzzed = true;
				}

				// recupero sicuro username
				String ownerUsername = "utente bibliomap";
				if (ownerInfo != null && ownerInfo.getString("username") != null) {
					ownerUsername = ownerInfo.getString("username");
				}

				// estrazione tags
				List<String> tags = copy.getList("tags", String.class);
				if (tags == null) tags = new ArrayList<>();

				results.add(new BookMapDTO(
						copy.getString("_id"),
						book.getString("title"),
						book.getString("author"),
						lib.getString("name"),
						finalLat,
						finalLng,
						doc.getDouble("distance") / 1000.0,
						isFuzzed,
						book.getString("cover"), 
						lib.getString("ownerId"),
						ownerUsername,
						tags 
						));

			} catch (Exception e) {
				LOG.error("errore mapping libro: " + e.getMessage());
			}
		}

		return results;
	}


	public BookDetailDTO getBookDetails(String copyId) {

		MongoCollection<Document> copiesCol = mongoClient.getDatabase("bibliomap").getCollection("copies");
		List<Bson> pipeline = new ArrayList<>();

		// 1. Trova la copia specifica
		pipeline.add(Aggregates.match(Filters.eq("_id", copyId)));

		// 2. Join con Tabella Libri (per titolo, autore, cover...)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "books")
				.append("localField", "book_isbn")
				.append("foreignField", "_id")
				.append("as", "bookInfo")));
		pipeline.add(new Document("$unwind", "$bookInfo"));

		// 3. Join con Tabella Librerie (per nome libreria e ownerId)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "libraries")
				.append("localField", "libraryId")
				.append("foreignField", "_id")
				.append("as", "libraryInfo")));
		pipeline.add(new Document("$unwind", "$libraryInfo"));

		// 4. Join con Utenti (per prendere lo username del proprietario)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "users")
				.append("localField", "libraryInfo.ownerId")
				.append("foreignField", "_id")
				.append("as", "ownerInfo")));

		// Unwind "safe": se l'utente non esiste più, non rompe tutto
		pipeline.add(new Document("$unwind", new Document("path", "$ownerInfo").append("preserveNullAndEmptyArrays", true)));

		// Esecuzione Query
		Document result = copiesCol.aggregate(pipeline).first();

		if (result == null) return null;

		return mapToDetailDTO(result);
	}

	private BookDetailDTO mapToDetailDTO(Document doc) {

		Document book = doc.get("bookInfo", Document.class);
		Document lib = doc.get("libraryInfo", Document.class);
		Document owner = doc.get("ownerInfo", Document.class);

		// cover logic
		String rawCover = book.getString("cover");
		String finalCover = null;
		if (rawCover != null && !rawCover.isEmpty()) {
			finalCover = rawCover.startsWith("data:") ? rawCover : "data:image/jpeg;base64," + rawCover;
		}

		// username logic
		String username = (owner != null) ? owner.getString("username") : "Utente Bibliomap";

		return new BookDetailDTO(
				doc.getString("_id"),
				book.getString("_id"),
				book.getString("title"),
				book.getString("author"),
				finalCover,
				book.getInteger("publication_year", 0), 
				book.getString("language"),
				book.getString("cover_type"), 
				book.getString("publisher"),

				lib.getString("name"),
				lib.getString("_id"),
				lib.getString("ownerId"), 
				username,

				doc.getString("condition"),
				doc.getString("status"),
				doc.getString("owner_notes"),
				doc.getList("tags", String.class)
				);
	}


	public Optional<Book> findExistingBook(String author, String title, int year, String publisher, String language) {

		
		
		
		return null;
	}
}