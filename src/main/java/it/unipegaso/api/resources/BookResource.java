package it.unipegaso.api.resources;

import java.util.List;
import java.util.Optional;

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.model.Book;
import it.unipegaso.service.BookService;
import it.unipegaso.service.GoogleBooksService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/books")
@Produces(MediaType.APPLICATION_JSON)
public class BookResource {

	@Inject
	BookService bookService;

	@Inject
	GoogleBooksService googleBooksService;

	@Inject
    BooksRepository bookRepository;

	@GET
	@Path("/nearby")
	public Response getNearbyBooks(
			@QueryParam("lat") Double lat, 
			@QueryParam("lng") Double lng, 
			@QueryParam("radius") Double radius,
			@QueryParam("visibility") String visibility,
			@QueryParam("exclude_user") String excludeUserId,
			@QueryParam("search") String searchText,
			@QueryParam("sort") String sortBy
			) {

		if (lat == null || lng == null) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\": \"Coordinate mancanti\"}").build();
		}
		if (radius == null) radius = 10.0; // Aumentiamo un po' il default a 10km
		if (visibility == null) visibility = "public";
		if (sortBy == null) sortBy = "distance"; // Default sorting

		List<BookMapDTO> books = bookService.searchBooks(lat, lng, radius, visibility, excludeUserId, searchText, sortBy);

		return Response.ok(books).build();
	}


	
	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getBook(@PathParam("id") String copyId) {

		BookDetailDTO detail = bookService.getBookDetails(copyId);

		if (detail == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		return Response.ok(detail).build();
	}
	

	@GET
	@Path("/external/lookup-metadata")
	@Produces(MediaType.APPLICATION_JSON)
	public Response lookupBookMetadata(@QueryParam("isbn") String isbn) {
		//TODO CHECK TUTTO
		//controllo se presente nel db
		Optional<Book> optBook = bookRepository.get(isbn);

		BookDetailDTO detail = null;

        if(optBook.isPresent()) {
            Book book = optBook.get();
            

            detail = new BookDetailDTO(
                isbn, // viene usato come id
                isbn,
                book.title,
                book.author,
                book.cover,
                book.publication_year,
                book.language,
                book.cover_type, 
                book.publisher,
                null, // libraryName
                null, // libraryId
                null, // ownerId
                null, // ownerName
                null, // condition
                null, // status
                null, // ownerNotes
                null  // tags
                );
        }
		else{
			//altrimenti chiamata al servizio google
			 detail = googleBooksService.lookupBookMetadata(isbn);
		}

		if (detail == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		return Response.ok(detail).build();

	}

}