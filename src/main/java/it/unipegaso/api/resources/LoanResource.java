package it.unipegaso.api.resources;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.LoanDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.api.util.StringUtils;
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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.PATCH;
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
			String title = book.getTitle();

			//crea richiesta
			Loan loanRequest = new Loan();

			loanRequest.setCopyId(copyId);
			loanRequest.setRequesterId(requesterId);
			loanRequest.setOwnerId(ownerId);
			loanRequest.setStatus(LoanStatus.PENDING.toString());
			loanRequest.setTitle(title);

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
			boolean success = emailService.sendLoanRequestEmail(owner.getEmail(), owner.getUsername(), user.getUsername(), title, book.getAuthor(), loanId);

			if(!success) {
				LOG.error("impossibile inviare email per richiesta prestito, cancello dal db la richiesta");
				loansRepository.delete(loanId);
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("SERVER_ERROR", "errore invio email"))
						.build();
			}

			Map<String, String> response = new HashMap<>();
			response.put("loanId", loanId);

			return Response.status(Response.Status.OK).entity(response).build();

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


	@POST
	@Path("/{id}/start")
	public Response startLoan(@Context HttpHeaders headers, @PathParam("id") String loanId) {

		LOG.debug("START LOAN");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User currentUser = userService.getUserFromSession(sessionId);

			// check loan id
			if (StringUtils.isEmpty(loanId)) {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ErrorResponse("BAD_REQUEST", "loan id mandatory")).build();
			}

			Optional<Loan> opLoan = loansRepository.get(loanId);
			if (opLoan.isEmpty()) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			Loan loan = opLoan.get();

			// verifica che sia il proprietario del libro
			if (!loan.getOwnerId().equals(currentUser.getId())) {
				LOG.warn("Utente " + currentUser.getId() + " ha tentato di gestire prestito non suo: " + loanId);
				return Response.status(Response.Status.FORBIDDEN)
						.entity(new ErrorResponse("FORBIDDEN", "Non sei il proprietario di questo prestito")).build();
			}

			// verifica status
			if (!LoanStatus.ACCEPTED.toString().equals(loan.getStatus())) {
				return Response.status(Response.Status.CONFLICT)
						.entity(new ErrorResponse("CONFLICT", "La richiesta è già stata processata")).build();
			}

			String copyId = loan.getCopyId();
			Optional<Copy> opCopy = copiesRepository.get(copyId);

			// controlla copia
			if (opCopy.isEmpty()) {

				loan.setStatus(LoanStatus.ERROR.toString());
				loansRepository.update(loan);
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("NOT_FOUND", "La copia del libro non e' stata trovata")).build();
			}

			// update loan status e aggiunte date
			Calendar cal = Calendar.getInstance();
			loan.setLoanStartDate(cal.getTime());

			cal.add(Calendar.DAY_OF_MONTH, 30); //il prestito dura 30 giorni
			loan.setExpectedReturnDate(cal.getTime());

			loan.setStatus(LoanStatus.ON_LOAN.toString());

			boolean success = loansRepository.update(loan);

			if (!success) {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
			}

			Copy copy = opCopy.get();
			copy.setStatus("on_loan");

			success = copiesRepository.update(copy);

			// handle copy update failure
			if (!success) {
				LOG.warn("IMPOSSIBILE AGGIORNARE COPIA");
				// manual rollback
				loan.setStatus(LoanStatus.ACCEPTED.toString());
				loan.setLoanStartDate(null);
				loan.setExpectedReturnDate(null);
				loansRepository.update(loan);

				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("UPDATE_FAILED", "Impossibile aggiornare stato copia, operazione annullata")).build();
			}

			return Response.ok().build();

		} catch (NotAuthorizedException e) {
			return e.getResponse();
		} catch (Exception e) {
			LOG.error("errore sconosciuto manage request", e);
			return Response.serverError().build();
		}
	}


	@PATCH
	@Path("/{id}/status")
	public Response manageRequest(@Context HttpHeaders headers, @PathParam("id") String loanId, Map<String, String> request) {

		LOG.debug("MANAGE LOAN REQUEST");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User currentUser = userService.getUserFromSession(sessionId);

			if (request == null || !request.containsKey("action")) {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ErrorResponse("BAD_REQUEST", "action mandatory")).build();
			}

			String action = (String) request.getOrDefault("action", "");

			if(StringUtils.isEmpty(action) || (!"ACCEPT".equalsIgnoreCase(action) && !"REJECT".equalsIgnoreCase(action))) {
				LOG.error("richiesta errata action [" + action + "]");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ErrorResponse("BAD_REQUEST", "action not allowed")).build();
			}

			if(StringUtils.isEmpty(loanId)) {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ErrorResponse("BAD_REQUEST", "loan id mandatory")).build();
			}

			Optional<Loan> opLoan = loansRepository.get(loanId);
			if(opLoan.isEmpty()) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			Loan loan = opLoan.get();

			// controllo di sicurezza 
			if (!loan.getOwnerId().equals(currentUser.getId())) {
				LOG.warn("Utente " + currentUser.getId() + " ha tentato di gestire prestito non suo: " + loanId);
				return Response.status(Response.Status.FORBIDDEN)
						.entity(new ErrorResponse("FORBIDDEN", "Non sei il proprietario di questo prestito")).build();
			}

			// controllo coerenza stato 
			if (!LoanStatus.PENDING.toString().equals(loan.getStatus())) {
				return Response.status(Response.Status.CONFLICT)
						.entity(new ErrorResponse("CONFLICT", "La richiesta è già stata processata")).build();
			}

			// Cambio stato
			String newStatus = "ACCEPT".equalsIgnoreCase(action) ? LoanStatus.ACCEPTED.toString() : LoanStatus.REJECTED.toString();
			loan.setStatus(newStatus);

			String notes = request.getOrDefault("notes", "");

			if(!StringUtils.isEmpty(notes)) {
				loan.setOwnerNotes(notes);
			}

			boolean success = loansRepository.update(loan);
			if(!success) {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
			}

			// Recupero dati per email
			String requesterID = loan.getRequesterId();
			Optional<User> opRequester = userRepository.get(requesterID);

			if(opRequester.isEmpty()) {
				// Edge case: il richiedente non esiste piu', loggo errore
				LOG.warn("Requester not found for loan " + loanId);
				return Response.ok().build(); 
			}

			User requester = opRequester.get();
			String title = loan.getTitle();

			success = emailService.sendRequestResponseEmail(requester.getEmail(), requester.getUsername(), title, action);

			if(!success) {
				LOG.error("impossibile inviare email, rollback sul db");
				loan.setStatus(LoanStatus.PENDING.toString());
				loansRepository.update(loan); // rollback stato
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("SERVER_ERROR", "errore invio notifica")).build();
			}

			return Response.ok().build();

		} catch(NotAuthorizedException e) {
			return e.getResponse();
		} catch (Exception e) {
			LOG.error("errore sconosciuto manage request", e);
			return Response.serverError().build();
		}
	}

	@POST
	@Path("/{id}/return")
	public Response closeLoan(@Context HttpHeaders headers, @PathParam("id") String loanId, Map<String, String> request) {

		LOG.debug("CLOSE LOAN");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User currentUser = userService.getUserFromSession(sessionId);

			if (StringUtils.isEmpty(loanId)) {
				return Response.status(Response.Status.BAD_REQUEST).build();
			}

			Optional<Loan> opLoan = loansRepository.get(loanId);
			if (opLoan.isEmpty()) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			Loan loan = opLoan.get();

			// controllo proprietario copia
			if (!loan.getOwnerId().equals(currentUser.getId())) {
				return Response.status(Response.Status.FORBIDDEN).build();
			}

			// controllo stato coerente
			if (!LoanStatus.ON_LOAN.toString().equals(loan.getStatus())) {
				return Response.status(Response.Status.CONFLICT)
						.entity(new ErrorResponse("CONFLICT", "il prestito non e' in corso")).build();
			}

			String conditionEnd = request.getOrDefault("condition", "ottimo");
			String oldStatus = loan.getStatus();

			// aggiornamento dati prestito
			loan.setStatus("RETURNED");
			loan.setActualReturnDate(new Date());

			if (!loansRepository.update(loan)) {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
			}

			// aggiornamento stato copia
			Optional<Copy> opCopy = copiesRepository.get(loan.getCopyId());
			if (opCopy.isPresent()) {
				Copy copy = opCopy.get();
				copy.setStatus("available");
				copy.setCondition(conditionEnd);
				copiesRepository.update(copy);
			}

			// notifica email restituzione
			boolean mailSuccess = false;
			try {
				Optional<User> opRequester = userRepository.get(loan.getRequesterId());
				if (opRequester.isPresent()) {
					User requester = opRequester.get();
					// invio mail di fine prestito
					mailSuccess = emailService.sendReturnConfirmationEmail(requester.getEmail(), requester.getUsername(), loan.getTitle());
				}
			} catch (Exception e) {
				LOG.error("errore invio mail restituzione", e);
			}

			if (!mailSuccess) {
				// rollback stato prestito
				loan.setStatus(oldStatus);
				loan.setActualReturnDate(null);
				loansRepository.update(loan);

				// rollback stato copia
				if (opCopy.isPresent()) {
					Copy copy = opCopy.get();
					copy.setStatus("on_loan");
					copiesRepository.update(copy);
				}

				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("SERVER_ERROR", "errore notifica, operazione annullata")).build();
			}

			return Response.ok().build();

		} catch (NotAuthorizedException e) {
			return e.getResponse();
		} catch (Exception e) {
			LOG.error("errore chiusura prestito", e);
			return Response.serverError().build();
		}
	}


	@GET
	@Path("/requests/incoming")
	public Response getIncomingRequests(@Context HttpHeaders headers) {

		LOG.debug("GET INCOMING REQUESTS");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User currentUser = userService.getUserFromSession(sessionId);

			// recupera richieste dove l'utente e' proprietario e lo stato e' PENDING
			List<Loan> requests = loansRepository.findIncomingByOwner(currentUser.getId());

			List<LoanDTO> response = new ArrayList<>();

			for (Loan loan: requests) {

				LoanDTO dto = loanToDTO(loan);

				response.add(dto);
			}

			return Response.ok(response).build();

		} catch (NotAuthorizedException e) {
			return e.getResponse();
		} catch (Exception e) {
			LOG.error("errore recupero richieste in entrata", e);
			return Response.serverError().build();
		}
	}




	@GET
	@Path("/active")
	public Response getActiveLoans(@Context HttpHeaders headers) {

		LOG.debug("GET ACTIVE LOANS");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User currentUser = userService.getUserFromSession(sessionId);

			// recupera prestiti in corso (stato ON_LOAN) sia come proprietario che come richiedente
			List<Loan> activeLoans = loansRepository.findActiveByUser(currentUser.getId());
			
			List<LoanDTO> response = new ArrayList<>();

			for (Loan loan: activeLoans) {

				LoanDTO dto = loanToDTO(loan);
				response.add(dto);
			}
			
			return Response.ok(response).build();

		} catch (NotAuthorizedException e) {
			return e.getResponse();
		} catch (Exception e) {
			LOG.error("errore recupero prestiti attivi", e);
			return Response.serverError().build();
		}
	}

	@GET
	@Path("/all")
	public Response getAllLoans(@Context HttpHeaders headers) {

		LOG.debug("GET ALL LOANS");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User currentUser = userService.getUserFromSession(sessionId);

			// recupera tutti prestiti sia come proprietario che come richiedente
			List<Loan> allLoans = loansRepository.findAllUserLoans(currentUser.getId());

			List<LoanDTO> response = new ArrayList<>();

			for (Loan loan: allLoans) {

				LoanDTO dto = loanToDTO(loan);
				response.add(dto);
			}

			return Response.ok(response).build();

		} catch (NotAuthorizedException e) {
			return e.getResponse();
		} catch (Exception e) {
			LOG.error("errore recupero prestiti", e);
			return Response.serverError().build();
		}
	}

	private LoanDTO loanToDTO(Loan loan) {

		String requesterId = loan.getRequesterId();
		Optional<User> opRequester = userRepository.get(requesterId);
		String requesterUsername = "utente anonimo";

		if(opRequester.isPresent()) {
			requesterUsername = opRequester.get().getUsername();
		}

		String ownerId = loan.getOwnerId();
		Optional<User> opOwner = userRepository.get(ownerId);

		String ownerUsername = "utente anonimo";

		if(opOwner.isPresent()) {
			ownerUsername = opOwner.get().getUsername();
		}

		LoanDTO dto = new LoanDTO(
				loan.getId(),
				loan.getTitle(),
				requesterId,
				ownerId,
				loan.getCopyId(), 
				loan.getStatus(), 
				loan.getLoanStartDate(), 
				loan.getExpectedReturnDate(),
				loan.getOwnerNotes(), 
				ownerUsername, 
				requesterUsername);
		return dto;
	}

}
