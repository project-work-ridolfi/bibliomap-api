package it.unipegaso.database;

import java.util.Optional;

import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;

import it.unipegaso.database.model.Book;
import jakarta.inject.Inject;

public class BooksRepository implements IRepository<Book> {

	private static final Logger LOG = Logger.getLogger(BooksRepository.class);

    @Inject
    MongoCollection<Book> books;

    @Override
    public String create(Book book) throws MongoWriteException {
        if (book.isbn == null || book.isbn.isEmpty()) {
            LOG.error("Tentativo di inserire libro senza ISBN");
            return null;
        }

        InsertOneResult result = books.insertOne(book);

        if (!result.wasAcknowledged()) {
            LOG.error("Inserimento libro non confermato");
            return null;
        }
        return book.isbn;
    }

    @Override
    public Optional<Book> get(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(books.find(Filters.eq("_id", isbn)).first());
    }
}
