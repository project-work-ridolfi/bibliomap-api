package it.unipegaso.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.api.dto.BookMapDTO;
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

    /**
     * Esegue la query geospaziale complessa e ritorna DTO pronti.
     */
    public List<BookMapDTO> findNearbyBooks(double lat, double lng, double radiusKm, List<String> allowedVisibilities) {
        
        // Pipeline MongoDB
        Document geoNear = new Document("$geoNear", new Document()
            .append("near", new Document("type", "Point").append("coordinates", Arrays.asList(lng, lat)))
            .append("distanceField", "distance")
            .append("maxDistance", radiusKm * 1000) // Metri
            .append("spherical", true)
        );

        // Join con Libraries
        Document lookupLibraries = new Document("$lookup", new Document()
            .append("from", "libraries")
            .append("localField", "_id")
            .append("foreignField", "locationId")
            .append("as", "library")
        );
        Document unwindLibrary = new Document("$unwind", "$library");
        
        // Filtro visibilità
        Document matchVisibility = new Document("$match", 
            new Document("library.visibility", new Document("$in", allowedVisibilities))
        );

        // Join con Copies
        Document lookupCopies = new Document("$lookup", new Document()
            .append("from", "copies")
            .append("localField", "library._id")
            .append("foreignField", "libraryId")
            .append("as", "copy")
        );
        Document unwindCopy = new Document("$unwind", "$copy");

        // Join con Books (per titolo/autore)
        Document lookupBook = new Document("$lookup", new Document()
            .append("from", "books")
            .append("localField", "copy.book_isbn")
            .append("foreignField", "_id")
            .append("as", "bookInfo")
        );
        Document unwindBook = new Document("$unwind", "$bookInfo");

        List<Bson> pipeline = Arrays.asList(
            geoNear, lookupLibraries, unwindLibrary, matchVisibility, 
            lookupCopies, unwindCopy, lookupBook, unwindBook
        );

        List<BookMapDTO> results = new ArrayList<>();
        Random rand = new Random();

        // aggregazione trattando i risultati come Document generici
        for (Document doc : locations.withDocumentClass(Document.class).aggregate(pipeline)) {
            try {
                Document lib = doc.get("library", Document.class);
                Document copy = doc.get("copy", Document.class);
                Document book = doc.get("bookInfo", Document.class);
                Document geo = doc.get("geolocation", Document.class);
                List<Double> coords = geo.getList("coordinates", Double.class);

                double realLng = coords.get(0);
                double realLat = coords.get(1);
                
                // Gestione Blur/Privacy
                int blurRadius = lib.getInteger("blurRadius", 0);
                double finalLat = realLat;
                double finalLng = realLng;
                boolean isFuzzed = false;

                if (blurRadius > 0) {
                    double offset = (double) blurRadius / 111000.0; // approx metri in gradi
                    finalLat += (rand.nextDouble() * 2 - 1) * offset;
                    finalLng += (rand.nextDouble() * 2 - 1) * offset;
                    isFuzzed = true;
                }

                results.add(new BookMapDTO(
                    copy.getString("_id"),
                    book.getString("title"),
                    book.getString("author"),
                    lib.getString("name"),
                    finalLat,
                    finalLng,
                    doc.getDouble("distance") / 1000.0, // Converte in KM
                    isFuzzed
                ));
            } catch (Exception e) {
                LOG.warn("Errore mappatura risultato aggregazione: " + e.getMessage());
            }
        }

        return results;
    }
}