package it.unipegaso.service;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.LocationsRepository;
import it.unipegaso.database.model.Book;
import it.unipegaso.database.model.Copy;
import it.unipegaso.database.model.Library;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BookService {

	private static final Logger LOG = Logger.getLogger(BookService.class);
	private static final int MAX_IMG_SIZE = 400; // Pixel lato massimo

	@Inject
	LocationsRepository locationsRepository;

	@Inject
	BooksRepository booksRepository;

	@Inject
	CopiesRepository copiesRepository; 

	@Inject
	LibrariesRepository librariesRepository; 

	
	@Inject
	MongoClient mongoClient;

	public List<BookMapDTO> searchBooks(double lat, double lng, double radiusKm, String visibilityFilter, String excludeUserId, String searchText, String sortBy) {
		MongoCollection<Document> locationsCol = mongoClient.getDatabase("bibliomap").getCollection("locations");
		List<Bson> pipeline = new ArrayList<>();

		// 1. GeoNear
		Document geoNear = new Document("$geoNear", new Document()
				.append("near", new Document("type", "Point").append("coordinates", Arrays.asList(lng, lat)))
				.append("distanceField", "distance")
				.append("maxDistance", radiusKm * 1000)
				.append("spherical", true));
		pipeline.add(geoNear);

		// 2. Lookup Library
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "libraries")
				.append("localField", "_id")
				.append("foreignField", "locationId")
				.append("as", "library")));
		pipeline.add(new Document("$unwind", "$library"));

		// 3. Filtri preliminari su Library
		List<Bson> libraryFilters = new ArrayList<>();
		List<String> allowedVisibilities = new ArrayList<>(Arrays.asList("all"));
		if ("logged-in".equals(visibilityFilter)) allowedVisibilities.add("logged-in");
		libraryFilters.add(Filters.in("library.visibility", allowedVisibilities));

		if (excludeUserId != null && !excludeUserId.trim().isEmpty()) {
			libraryFilters.add(Filters.ne("library.ownerId", excludeUserId));
		}
		pipeline.add(Aggregates.match(Filters.and(libraryFilters)));

		// 4. Lookup Owner (User)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "users")
				.append("localField", "library.ownerId")
				.append("foreignField", "_id")
				.append("as", "ownerInfo")));
		pipeline.add(new Document("$unwind", new Document("path", "$ownerInfo").append("preserveNullAndEmptyArrays", true)));

		// 5. Lookup Copies
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "copies")
				.append("localField", "library._id")
				.append("foreignField", "libraryId")
				.append("as", "copy")));
		pipeline.add(new Document("$unwind", "$copy"));

		// 6. Lookup Books (usando copy.book_isbn -> books._id)
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "books")
				.append("localField", "copy.book_isbn")
				.append("foreignField", "_id")
				.append("as", "bookInfo")));
		pipeline.add(new Document("$unwind", "$bookInfo"));

		// 7. Ricerca Testuale
		if (searchText != null && !searchText.trim().isEmpty()) {
			Pattern regex = Pattern.compile(Pattern.quote(searchText), Pattern.CASE_INSENSITIVE);
			pipeline.add(Aggregates.match(Filters.or(
					Filters.regex("bookInfo.title", regex),
					Filters.regex("bookInfo.author", regex))));
		}

		// 8. Sorting
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

				// Privacy blur
				int libBlur = lib.getInteger("blurRadius", 0);
				int userBlur = (ownerInfo != null) ? ownerInfo.getInteger("blurRadius", 0) : 0;
				int effectiveBlur = Math.max(libBlur, userBlur);

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

				String ownerUsername = (ownerInfo != null) ? ownerInfo.getString("username") : "utente bibliomap";
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
						tags));

			} catch (Exception e) {
				LOG.error("errore mapping libro: " + e.getMessage());
			}
		}
		return results;
	}

	public BookDetailDTO getBookDetails(String copyId) {
		MongoCollection<Document> copiesCol = mongoClient.getDatabase("bibliomap").getCollection("copies");
		List<Bson> pipeline = new ArrayList<>();

		pipeline.add(Aggregates.match(Filters.eq("_id", copyId)));

		// Lookup su Books usando book_isbn
		pipeline.add(new Document("$lookup", new Document()
				.append("from", "books")
				.append("localField", "book_isbn")
				.append("foreignField", "_id")
				.append("as", "bookInfo")));
		pipeline.add(new Document("$unwind", "$bookInfo"));

		pipeline.add(new Document("$lookup", new Document()
				.append("from", "libraries")
				.append("localField", "libraryId")
				.append("foreignField", "_id")
				.append("as", "libraryInfo")));
		pipeline.add(new Document("$unwind", "$libraryInfo"));

		pipeline.add(new Document("$lookup", new Document()
				.append("from", "users")
				.append("localField", "libraryInfo.ownerId")
				.append("foreignField", "_id")
				.append("as", "ownerInfo")));
		pipeline.add(new Document("$unwind", new Document("path", "$ownerInfo").append("preserveNullAndEmptyArrays", true)));

		Document result = copiesCol.aggregate(pipeline).first();
		if (result == null) return null;

		return mapToDetailDTO(result);
	}

	private BookDetailDTO mapToDetailDTO(Document doc) {
		Document book = doc.get("bookInfo", Document.class);
		Document lib = doc.get("libraryInfo", Document.class);
		Document owner = doc.get("ownerInfo", Document.class);

		// Controllo se è base64 o url
		String rawCover = book.getString("cover");
		String finalCover = null;
		if (rawCover != null && !rawCover.isEmpty()) {
			finalCover = rawCover.startsWith("http") ? rawCover : (rawCover.startsWith("data:") ? rawCover : "data:image/jpeg;base64," + rawCover);
		}

		String username = (owner != null) ? owner.getString("username") : "Utente Bibliomap";

		return new BookDetailDTO(
				doc.getString("_id"),
				book.getString("_id"), // ISBN
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
				doc.getList("tags", String.class));
	}

	public List<Book> findExistingBooks(String author, String title, int year, String publisher, String language) {
		List<Bson> conditions = new ArrayList<>();
		conditions.add(Filters.eq("author", author));
		conditions.add(Filters.eq("title", title));

		if (year > 0) conditions.add(Filters.eq("publication_year", year));
		if (publisher != null && !publisher.trim().isEmpty()) conditions.add(Filters.eq("publisher", publisher));
		if (language != null && !language.trim().isEmpty()) conditions.add(Filters.eq("language", language));

		return booksRepository.find(Filters.and(conditions)).into(new ArrayList<>());
	}


	public boolean saveBookWithBase64Cover(BookDetailDTO dto, FileUpload coverFile) {
		// Validazione LibraryID
		if (dto.libraryId() == null) {
			return false;
		}

		try {
			Book book = findOrCreateBook(dto, coverFile);

			Optional<Library> libraryOpt = librariesRepository.get(dto.libraryId());
			if (libraryOpt.isEmpty()) {
				LOG.warn("Tentativo di salvataggio su libreria inesistente: " + dto.libraryId());
				return false;
			}

			Copy copy = new Copy();

			copy.setId(java.util.UUID.randomUUID().toString());

			copy.setBookIsbn(book.getIsbn()); // Link foreign key logica verso Book
			copy.setLibraryId(dto.libraryId()); // Link foreign key logica verso Library

			copy.setCondition(dto.condition());
			copy.setStatus(dto.status());
			copy.setOwnerNotes(dto.ownerNotes());
			if (dto.tags() != null) {
				copy.setTags(dto.tags());
			}

			copiesRepository.create(copy);
			return true;

		} catch (Exception e) {
			LOG.error("Errore salvataggio libro/copia", e);
			return false;
		}
	}

	private Book findOrCreateBook(BookDetailDTO dto, FileUpload coverFile) {
		Book book = null;

		// cerca per ISBN (Primary Key)
		if (dto.isbn() != null && !dto.isbn().isBlank()) {
			Optional<Book> existing = booksRepository.get(dto.isbn());
			if (existing.isPresent()) {
				book = existing.get();
			}
		}


		//  se null, crea nuovo
		if (book == null) {
			book = new Book();

			book.setIsbn(dto.isbn()); 
			book.setTitle(dto.title());
			book.setAuthor(dto.author());
			book.setPublisher(dto.publisher());
			book.setPublication_year(dto.publicationYear() != null ? dto.publicationYear() : 0);
			book.setLanguage(dto.language());

			// --- LOGICA IMMAGINE BASE64 ---
			String base64Image = processImageToBase64(coverFile);

			if (base64Image != null) {
				// Salviamo direttamente la stringa base64 nel campo 'cover'
				book.setCover(base64Image);
			} else if (dto.coverUrl() != null) {
				// Fallback URL esterno
				book.setCover(dto.coverUrl());
			}

			booksRepository.create(book);
		}

		return book;
	}

	private String processImageToBase64(FileUpload file) {
		if (file == null || file.fileName() == null) return null;

		java.nio.file.Path tempPath = file.uploadedFile();

		try {
			BufferedImage originalImage = ImageIO.read(tempPath.toFile());

			if (originalImage == null) {
				return null; 
			}

			int originalWidth = originalImage.getWidth();
			int originalHeight = originalImage.getHeight();
			int newWidth = originalWidth;
			int newHeight = originalHeight;

			// Logica di ridimensionamento (Resize)
			if (originalWidth > MAX_IMG_SIZE || originalHeight > MAX_IMG_SIZE) {
				if (originalWidth > originalHeight) {
					newWidth = MAX_IMG_SIZE;
					newHeight = (newWidth * originalHeight) / originalWidth;
				} else {
					newHeight = MAX_IMG_SIZE;
					newWidth = (newHeight * originalWidth) / originalHeight;
				}
			}

			// Creazione immagine scalata
			Image resultingImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
			BufferedImage outputImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = outputImage.createGraphics();
			g2d.drawImage(resultingImage, 0, 0, null);
			g2d.dispose();

			// Conversione in Base64
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			// Scrive come JPG per risparmiare spazio nel documento Mongo
			ImageIO.write(outputImage, "jpg", os); 
			String b64 = Base64.getEncoder().encodeToString(os.toByteArray());

			return "data:image/jpeg;base64," + b64;

		} catch (Exception e) {
			LOG.error("Errore elaborazione immagine: " + e.getMessage());
			return null;
		} finally {
			// Elimina il file temporaneo creato dal framework
			try {
				java.nio.file.Files.deleteIfExists(tempPath);
			} catch (Exception ex) {
				LOG.warn("Impossibile eliminare il file temporaneo: " + tempPath + " - " + ex.getMessage());
			}
		}
	}

	public List<BookDetailDTO> getBooksByLibrary(String libraryId) {
		LOG.debug("recupero libri per libreria: " + libraryId);

		// recupero tutte le copie associate alla libreria
		List<Copy> copies = copiesRepository.findByLibrary(libraryId);
		List<BookDetailDTO> results = new ArrayList<>();

		
		for (Copy copy : copies) {
			// per ogni copia cerco i metadati del libro tramite isbn
			Optional<Book> bookOpt = booksRepository.get(copy.getBookIsbn());

			if (bookOpt.isPresent()) {
				Book book = bookOpt.get();

				// mappatura manuale verso BookDetailDTO
				results.add(new BookDetailDTO(
						copy.getId(),
						book.getIsbn(),
						book.getTitle(),
						book.getAuthor(),
						book.getCover(), // cover del libro base
						book.getPublication_year(),
						book.getLanguage(),
						book.getCover_type(),
						book.getPublisher(),
						null, // libraryName (gia' noto al chiamante)
						libraryId,
						null, // ownerId
						null, // ownerName
						copy.getCondition(),
						copy.getStatus(),
						copy.getOwnerNotes(),
						copy.getTags()
						));
			}
		}
		return results;
	}
}