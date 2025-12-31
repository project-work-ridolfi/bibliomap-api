package it.unipegaso.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.util.StringUtils;
import it.unipegaso.database.model.Book;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleBooksService {

	private static final Logger LOG = Logger.getLogger(GoogleBooksService.class);

	@Inject
	ObjectMapper objectMapper;


	@ConfigProperty(name = "quarkus.book-api.key")
	String apiKey;

	@ConfigProperty(name = "quarkus.book-api.url")
	String url;

	HttpClient client = HttpClient.newHttpClient();


	public BookDetailDTO lookupBookMetadata(String isbn) {
		try {
			// costruisce uri chiamata api google
			String requestUrl = String.format("%s?q=isbn:%s&key=%s", url, isbn, apiKey);

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(requestUrl))
					.GET()
					.build();

			// esegue chiamata sincrona
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				LOG.error("error fetching from google books: " + response.statusCode());
				return null;
			}

			// parsa risposta json
			JsonNode root = objectMapper.readTree(response.body());
			int totalItems = root.path("totalItems").asInt(0);

			LOG.debug("GOOGLE RESPONSE " + root);

			/*
			 * GOOGLE RESPONSE {
			 * "kind":"books#volumes",
			 * "totalItems":1,
			 * "items":[
			 * {"kind":"books#volume",
			 * "id":"dvNHngEACAAJ",
			 * "etag":"+feXnTquw6c",
			 * "selfLink":"https://www.googleapis.com/books/v1/volumes/dvNHngEACAAJ",
			 * "volumeInfo":{
			 * "title":"Orange is the new black. Da Manhattan al carcere: il mio anno dietro le sbarre",
			 * "authors":["Piper Kerman"],
			 * "publishedDate":"2014",
			 * "industryIdentifiers":[{"type":"ISBN_10","identifier":"8817072699"},{"type":"ISBN_13","identifier":"9788817072694"}],
			 * "readingModes":{"text":false,"image":false},
			 * "pageCount":427,
			 * "printType":"BOOK",
			 * "categories":["Biography & Autobiography"],
			 * "maturityRating":"NOT_MATURE","allowAnonLogging":false,"contentVersion":"preview-1.0.0","imageLinks":{"smallThumbnail":"http://books.google.com/books/content?id=dvNHngEACAAJ&printsec=frontcover&img=1&zoom=5&source=gbs_api","thumbnail":"http://books.google.com/books/content?id=dvNHngEACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api"},
			 * "language":"it",
			 * "previewLink":"http://books.google.it/books?id=dvNHngEACAAJ&dq=isbn:9788817072694&hl=&cd=1&source=gbs_api","infoLink":"http://books.google.it/books?id=dvNHngEACAAJ&dq=isbn:9788817072694&hl=&source=gbs_api",
			 * "canonicalVolumeLink":"https://books.google.com/books/about/Orange_is_the_new_black_Da_Manhattan_al.html?hl=&id=dvNHngEACAAJ"},
			 * "saleInfo":{"country":"IT","saleability":"NOT_FOR_SALE","isEbook":false},
			 * "accessInfo":{"country":"IT","viewability":"NO_PAGES","embeddable":false,"publicDomain":false,"textToSpeechPermission":"ALLOWED","epub":{"isAvailable":false},"pdf":{"isAvailable":false},"webReaderLink":"http://play.google.com/books/reader?id=dvNHngEACAAJ&hl=&source=gbs_api","accessViewStatus":"NONE","quoteSharingAllowed":false}}]}
			 */

			if (totalItems == 0) {
				// nessun libro trovato per questo isbn
				LOG.error("NON SONO PRESENTI LIBRI CON QUESTO ISBN" + isbn);
				return null;
			}

			// estrae il primo risultato utile
			JsonNode volumeInfo = root.path("items").get(0).path("volumeInfo");

			// estrae dati principali
			String title = volumeInfo.path("title").asText(null);
			String publisher = volumeInfo.path("publisher").asText(null);
			String language = volumeInfo.path("language").asText(null);

			// gestisce autori (da array a stringa singola)
			String author = "Sconosciuto";
			if (volumeInfo.has("authors")) {
				List<String> authorsList = new ArrayList<>();
				volumeInfo.path("authors").forEach(a -> authorsList.add(a.asText()));
				author = String.join(", ", authorsList);
			}

			// gestisce anno pubblicazione (potrebbe essere yyyy-mm-dd, serve solo anno)
			Integer publicationYear = null;
			String publishedDate = volumeInfo.path("publishedDate").asText();
			if (publishedDate != null && publishedDate.length() >= 4) {
				try {
					publicationYear = Integer.parseInt(publishedDate.substring(0, 4));
				} catch (NumberFormatException e) {
					LOG.warn("impossibile parsare anno: " + publishedDate);
				}
			}

			// gestisce copertina
			String cover = null;
			if (volumeInfo.has("imageLinks")) {
				// preferisce thumbnail a smallThumbnail
				cover = volumeInfo.path("imageLinks").path("thumbnail").asText();
				// fix per protocollo http vs https
				if (cover != null && cover.startsWith("http://")) {
					cover = cover.replace("http://", "https://");
				}
			}


			// ritorna dto popolato con soli metadati
			// i campi relativi alla copia vengono lasciati null
			return new BookDetailDTO(
					isbn, // viene usato come id
					isbn,
					title,
					author,
					cover,
					null,
					publicationYear,
					StringUtils.getFullLanguage(language),
					"paperback", // default o null
					publisher,
					null, // libraryName
					null, // libraryId
					null, // ownerId
					null, // ownerName
					null, // condition
					null, // status
					null, // ownerNotes
					null,  // tags
					0
					);

		} catch (Exception e) {
			LOG.error("eccezione durante lookup google books", e);
			return null;
		}
	}


	public List<Book> lookUpIsbn(String title, String author, String publisher, int year) {
	    
		List<Book> books = new ArrayList<>();
	    String query = buildGoogleBooksQuery(title, author, publisher, year);
	    String reqUrl = String.format("%s?q=%s&key=%s", url, query, apiKey);

	    HttpRequest request = HttpRequest.newBuilder()
	            .uri(URI.create(reqUrl))
	            .GET()
	            .build();

	    try {
	        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

	        if (response.statusCode() != 200) {
	            LOG.error("error fetching from google books: " + response.statusCode());
	            return books;
	        }

	        JsonNode root = objectMapper.readTree(response.body());
	        JsonNode items = root.path("items");

	        if (items.isMissingNode() || !items.isArray()) {
	            return books;
	        }

	        // ci fermiamo non appena ne abbiamo trovati 5 validi
	        for (int i = 0; i < items.size(); i++) {
	            if (books.size() >= 5) break; 

	            Book book = extractBook(root, i);
	            
	            // Verifichiamo che il libro estratto abbia un ISBN valido
	            if (book != null && book.getIsbn() != null && !book.getIsbn().isEmpty()) {
	                books.add(book);
	                LOG.debug("Aggiunto libro valido: " + book.getTitle() + " ISBN: " + book.getIsbn());
	            } else {
	                LOG.debug("Scartato risultato all'indice " + i + " perché privo di ISBN_13");
	            }
	        }

	        return books;

	    } catch (Exception e) {
	        LOG.error("eccezione durante lookup google books", e);
	        return books;
	    }
	}


	private Book extractBook(JsonNode root, int i) {

		// estrae il primo risultato utile
		JsonNode volumeInfo = root.path("items").get(i).path("volumeInfo");

		// estrae dati principali
		String title = volumeInfo.path("title").asText(null);
		String publisher = volumeInfo.path("publisher").asText(null);
		String language = volumeInfo.path("language").asText(null);
		String isbn = null; 

		JsonNode isbns = volumeInfo.path("industryIdentifiers");

		for(JsonNode node : isbns) {
			if("ISBN_13".equals(node.get("type").asText())) {
				isbn = node.get("identifier").asText(null);
			}
		}
		
		if (isbn == null) {
			return null;
		}

		// gestisce autori (da array a stringa singola)
		String author = "Sconosciuto";
		if (volumeInfo.has("authors")) {
			List<String> authorsList = new ArrayList<>();
			volumeInfo.path("authors").forEach(a -> authorsList.add(a.asText()));
			author = String.join(", ", authorsList);
		}

		// gestisce anno pubblicazione (potrebbe essere yyyy-mm-dd, serve solo anno)
		Integer publicationYear = null;
		String publishedDate = volumeInfo.path("publishedDate").asText();
		if (publishedDate != null && publishedDate.length() >= 4) {
			try {
				publicationYear = Integer.parseInt(publishedDate.substring(0, 4));
			} catch (NumberFormatException e) {
				LOG.warn("impossibile parsare anno: " + publishedDate);
			}
		}

		// gestisce copertina
		String cover = null;
		if (volumeInfo.has("imageLinks")) {
			// preferisce thumbnail a smallThumbnail
			cover = volumeInfo.path("imageLinks").path("thumbnail").asText();
			// fix per protocollo http vs https
			if (cover != null && cover.startsWith("http://")) {
				cover = cover.replace("http://", "https://");
			}
		}

		Book book = new Book();
		book.setIsbn(isbn);
		book.setAuthor(author);
		book.setCover(cover);
		book.setLanguage(StringUtils.getFullLanguage(language));
		
		if(publicationYear != null) {
			book.setPublication_year(publicationYear);
		}
		
		book.setPublisher(publisher);
		book.setTitle(title);

		return book;

	}


	public String buildGoogleBooksQuery(String title, String author, String publisher, int year) {

		StringBuilder qParam = new StringBuilder();

		// helper per gestire encoding e spazi
		// intitle:il+nome+della+rosa
		qParam.append("intitle:").append(URLEncoder.encode(title.trim(), StandardCharsets.UTF_8));

		if (!qParam.isEmpty()) qParam.append("+");
		qParam.append("inauthor:").append(URLEncoder.encode(author.trim(), StandardCharsets.UTF_8));

		if (publisher != null && !publisher.isBlank()) {
			if (!qParam.isEmpty()) qParam.append("+");
			qParam.append("inpublisher:").append(URLEncoder.encode(publisher.trim(), StandardCharsets.UTF_8));
		}

		// l'anno viene semplicemente aggiunto alla ricerca per rilevanza
		if (year > 0) {
			if (!qParam.isEmpty()) qParam.append("+");
			qParam.append(year);
		}


		return qParam.toString();
	}
}