package it.unipegaso.api.resources;

import java.util.List;
import java.util.Optional;

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.api.dto.BookMultipartBody;
import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.model.Book;
import it.unipegaso.service.BookService;
import it.unipegaso.service.GoogleBooksService;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
	
	@Inject 
	UserService userService;
	
	@Inject
	LibraryService libraryService;


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
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ErrorResponse("BAD_REQUEST", "Coordinate mancanti"))
					.build();
		}
		if (radius == null) radius = 10.0; 
		if (visibility == null) visibility = "public";
		if (sortBy == null) sortBy = "distance"; // Default sorting

		List<BookMapDTO> books = bookService.searchBooks(lat, lng, radius, visibility, excludeUserId, searchText, sortBy);

		return Response.ok(books).build();
	}
	
	

	@POST
	@Path("/save")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response save(BookMultipartBody request) { 
		try {
			if (request.book == null) {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ErrorResponse("BAD_REQUEST", "Parametri obbligatori assenti")).build();
			}

			boolean savedCopy = bookService.saveBookWithBase64Cover(request.book, request.coverFile);
			return Response.ok(savedCopy).build();

		} catch (IllegalArgumentException e) {
			return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("BAD_REQUEST", "Parametri malformati")).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().entity(new ErrorResponse("SERVER_ERROR", "internal server error")).build();
		}
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
	@Path("/external/search-isbn")
	@Produces(MediaType.APPLICATION_JSON)
	public Response lookupISBN(@QueryParam("author") String author,
			@QueryParam("title") String title,
			@QueryParam("year") int year,
			@QueryParam("publisher")String publisher,
			@QueryParam("language") String language) {

		if(author == null || title == null) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ErrorResponse("BAD_REQUEST", "Parametri obbligatori assenti"))
					.build();	
		}

		List<Book> bookList = bookService.findExistingBooks(author, title, year, publisher, language);

		if(bookList == null || bookList.isEmpty()) {
			//chiamiamo google service
			bookList = googleBooksService.lookUpIsbn(title, author, publisher, year);
		}


		return Response.ok(bookList).build();

	}


	@GET
	@Path("/external/lookup-metadata")
	@Produces(MediaType.APPLICATION_JSON)
	public Response lookupBookMetadata(@QueryParam("isbn") String isbn) {
		//controllo se presente nel db
		Optional<Book> optBook = bookRepository.get(isbn);

		BookDetailDTO detail = null;

		if(optBook.isPresent()) {
			Book book = optBook.get();


			detail = new BookDetailDTO(
					isbn, // viene usato come id
					isbn,
					book.getTitle(),
					book.getAuthor(),
					book.getCover(),
					null,
					book.getPublication_year(),
					book.getLanguage(),
					book.getCover_type(), 
					book.getPublisher(),
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