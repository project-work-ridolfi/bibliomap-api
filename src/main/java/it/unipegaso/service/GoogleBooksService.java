package it.unipegaso.service;

import it.unipegaso.api.dto.BookDetailDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GoogleBooksService {

    private static final Logger LOG = Logger.getLogger(GoogleBooksService.class);

    @Inject
    ObjectMapper objectMapper;

    
    @ConfigProperty(name = "quarkus.book-api.key")
    String apiKey;

    @ConfigProperty(name = "quarkus.book-api.url")
    String url;

    public BookDetailDTO lookupBookMetadata(String isbn) {
        try {
            // costruisce uri chiamata api google
            String requestUrl = String.format("%s?q=isbn:%s&key=%s", url, isbn, apiKey);
            
            // istanzia client http
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            // esegue chiamata sincrona
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.error("error fetching from google books: " + response.statusCode());
                return null;
            }

            // parsa risposta json
            JsonNode root = objectMapper.readTree(response.body());
            int totalItems = root.path("totalItems").asInt(0);

            if (totalItems == 0) {
                // nessun libro trovato per questo isbn
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
                    publicationYear,
                    language,
                    "paperback", // default o null
                    publisher,
                    null, // libraryName
                    null, // libraryId
                    null, // ownerId
                    null, // ownerName
                    null, // condition
                    null, // status
                    null, // ownerNotes
                    null  // tags
            );

        } catch (Exception e) {
            LOG.error("eccezione durante lookup google books", e);
            return null;
        }
    }
}