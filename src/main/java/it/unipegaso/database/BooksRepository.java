package it.unipegaso.database;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Sorts.descending;

import java.util.Arrays;
import java.util.HashMap;
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
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Book;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BooksRepository implements IRepository<Book> {

	private static final Logger LOG = Logger.getLogger(BooksRepository.class);

    @Inject
    MongoCollection<Book> books;

    @Override
    public String create(Book book) throws MongoWriteException {
        if (book.getIsbn() == null || book.getIsbn().isEmpty()) {
            LOG.error("Tentativo di inserire libro senza ISBN, ne vien creato uno ad hoc");
            
            book.setIsbn("fake_isbn_" + UUID.randomUUID().toString());
        }

        InsertOneResult result = books.insertOne(book);

        if (!result.wasAcknowledged()) {
            LOG.error("Inserimento libro non confermato");
            return null;
        }
        return book.getIsbn();
    }

    @Override
    public Optional<Book> get(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
        	return Optional.empty();
        }
        return Optional.ofNullable(books.find(Filters.eq("_id", isbn)).first());
    }
    
    @Override
    public FindIterable<Book>find (Bson filter){
    	return books.find(filter);
    }

	@Override
	public boolean update(Book obj) throws MongoWriteException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean delete(String id) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long count() {
		return 	books.countDocuments();
	}
	
	public Map<String, Long> getBooksByLanguage() {
	    List<Bson> pipeline = Arrays.asList(
	        group("$language", sum("count", 1)),
	        sort(descending("count"))
	    );

	    Map<String, Long> stats = new HashMap<>();
	    books.withDocumentClass(Document.class)
	         .aggregate(pipeline)
	         .forEach(doc -> stats.put(doc.getString("_id"), doc.getInteger("count").longValue()));
	    return stats;
	}
}
