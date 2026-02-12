package it.unipegaso.service;

import io.quarkus.test.junit.QuarkusTest;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.model.Book;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class UpdateCoversTest {

    private static final Logger LOG = Logger.getLogger(UpdateCoversTest.class);

    @Inject
    BookService bookService;

    @Inject
    BooksRepository booksRepository; 

    @Test
    public void testUpdateCover() {
    	
        // isbn del libro da bonificare
        String targetIsbn = "9781535200080";
        
        Optional<Book> bookPrima = booksRepository.get(targetIsbn);
        assertTrue(bookPrima.isPresent(), "non c'è nel db");
        
        String coverPrima = bookPrima.get().getCover();
        LOG.info("cover: " + coverPrima);
        

        String coverDopo = bookService.updateAndDownloadCoverFromUrl(coverPrima, targetIsbn);

        // check
        assertNotNull(coverDopo, "null");
        assertTrue(coverDopo.startsWith("data:image/jpeg;base64,"), 
            "cover restituita: " + (coverDopo.length() > 50 ? coverDopo.substring(0, 50) : coverDopo));

        // db
        Optional<Book> bookDopo = booksRepository.get(targetIsbn);
        String coverDb = bookDopo.get().getCover();
        
        
        assertEquals(coverDopo, coverDb, "Il database deve contenere la stringa Base64 aggiornata");
    }
    
}