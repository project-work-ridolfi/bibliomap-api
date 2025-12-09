package it.unipegaso.api.resources;

import java.util.Date;
import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Book;
import it.unipegaso.database.model.Copy;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.LoanStatus;
import it.unipegaso.database.model.User;
import it.unipegaso.service.EmailService;
import it.unipegaso.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/loan")
@Produces(MediaType.APPLICATION_JSON)
public class LoanResource {

	private static final Logger LOG = Logger.getLogger(LoanResource.class);

	@Inject 
	UserService userService;
	
	@Inject 
	UsersRepository userRepository;

	@Inject
	CopiesRepository copiesRepository;
	
	@Inject
	BooksRepository bookRepository;

	@Inject
	LibrariesRepository libraryRepository;

	@Inject
	EmailService emailService;
	
	@Inject
	LoansRepository loansRepository;


	@POST
	@Path("/{id}")
	public Response createLoanRequest(@PathParam("id") String copyId, @Context HttpHeaders headers) {

		LOG.debug("CREATE LOAN REQUEST FOR " + copyId);

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			//prendo l'utente
			User user = userService.getUserFromSession(sessionId);

			String requesterId = user.getId();

			//prendo la copia del libro
			Optional<Copy> opCopy = copiesRepository.get(copyId);	

			// se non la trovo ritorno subito 404
			if(opCopy.isEmpty()) {
				LOG.info("copy not found");
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			Copy copy = opCopy.get();

			String status = copy.getStatus();
			String bookId = copy.getBookIsbn();
			
			// se non è disponibile TODO usa enum
			if(!"available".equals(status)) {
				LOG.info("copy not available");
				return Response.status(Response.Status.CONFLICT)
						.entity(new ErrorResponse("SERVER_ERROR", "copia non disponibile al prestito")).build();
			}

			Optional<Library> opLib = libraryRepository.get(copy.getLibraryId());

			if(opLib.isEmpty()) {
				LOG.info("library not found");
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			Library lib = opLib.get();
			
			String ownerId = lib.getOwnerId();

			//crea richiesta
			Loan loanRequest = new Loan();

			loanRequest.setCopyId(copyId);
			loanRequest.setRequesterId(requesterId);
			loanRequest.setOwnerId(ownerId);
			loanRequest.setStatus(LoanStatus.PENDING.toString());

			Date now = new Date();
			loanRequest.setCreatedAt(now);

			//se e' andato tutto bene lo salvo
			String loanId = loansRepository.create(loanRequest);
			
			if(loanId == null) {
				LOG.error("impossibile salvare richiesta di prestito");
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("SERVER_ERROR", "errore db"))
						.build();
			}
			

			Optional<User> opOwner = userRepository.get(ownerId);
			
			if(opOwner.isEmpty()) {
				LOG.info("owner not found");
				return Response.status(Response.Status.NOT_FOUND).build();
			}
			
			User owner = opOwner.get();
			
			Optional<Book> opBook = bookRepository.get(bookId);
			if(opBook.isEmpty()) {
				LOG.info("book not found");
				return Response.status(Response.Status.NOT_FOUND).build();
			}
			
			Book book = opBook.get();
			
			boolean success = emailService.sendLoanRequestEmail(owner.getEmail(), owner.getUsername(), user.getUsername(), book.getTitle(), book.getAuthor(), loanId);

			if(!success) {
				LOG.error("impossibile inviare email per richiesta prestito, cancello dal db la richiesta");
				loansRepository.delete(loanId);
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("SERVER_ERROR", "errore invio email"))
						.build();
			}
			
			
			
			return Response.status(Response.Status.ACCEPTED).build();

		}catch(NotAuthorizedException e) {
			return e.getResponse();
		} catch (IllegalStateException e) {
			LOG.errorf("errore logica: %s", e.getMessage());
			return Response.status(Response.Status.UNAUTHORIZED) // 401 se l'utente non è valido
					.entity(new ErrorResponse("AUTH_ERROR", "utente non autenticato o non trovato"))
					.build();
		} catch (Exception e) {
			LOG.error("errore sconosciuto creazione richiesta di prestito", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ErrorResponse("SERVER_ERROR", "errore interno creazione richiesta prestito"))
					.build();
		}


	}

}
