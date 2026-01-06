package it.unipegaso.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.UserProfileDTO;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Book;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.User;
import it.unipegaso.database.model.VisibilityOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class UserService {

	private static final Logger LOG = Logger.getLogger(UserService.class); 

	@Inject
	SessionDataService sessionDataService;

	@Inject
	UsersRepository userRepository;

	@Inject
	LoansRepository loansRepository;

	@Inject
	LibrariesRepository librariesRepository;

	@Inject
	CopiesRepository copiesRepository;

	@Inject
	LocationService locationService;
	
	@Inject 
	BooksRepository booksRepository;



	public User getUserFromSession(String sessionId) {

		// verifica la presenza della sessione
		if(sessionId == null || sessionId.isEmpty()) {
			LOG.warn("Session ID mancante o vuoto.");
			throw new NotAuthorizedException(
					"SESSION_EXPIRED: Sessione utente scaduta o mancante.",
					Response.status(Response.Status.UNAUTHORIZED)
					.entity(new ErrorResponse("SESSION_EXPIRED", "Sessione utente scaduta o mancante."))
					.build()
					);
		}

		// recupera l'username 
		String username = sessionDataService.get(sessionId, "username").orElse(null);

		if(username == null) {
			LOG.warnf("Dati username mancanti in sessione per ID: %s", sessionId);
			// 401 Unauthorized 
			throw new NotAuthorizedException(
					"SESSION_DATA_MISSING: Dati username mancanti nella sessione.",
					Response.status(Response.Status.UNAUTHORIZED)
					.entity(new ErrorResponse("SESSION_DATA_MISSING", "Dati username mancanti nella sessione."))
					.build()
					);
		}

		Optional<User> userOpt = userRepository.findByUsername(username);

		if(userOpt.isEmpty()) {
			LOG.errorf("Impossibile trovare User DB tramite username: %s (Sessione: %s)", username, sessionId);

			// usa WebApplicationException per forzare il 500
			throw new WebApplicationException(
					Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ErrorResponse("USER_NOT_FOUND", "Utente registrato non trovato nel database."))
					.build()
					);
		}

		// Successo
		return userOpt.get();
	}

	public UserProfileDTO getProfile (String userId, boolean logged, boolean isOwner) {

		Optional<User> opuser = userRepository.get(userId);

		if(opuser.isEmpty()) {
			return null;
		}

		User user = opuser.get();

		String visibility = user.getVisibility();
		String userName = user.getUsername();
		int blurRadius = user.getBlurRadius();
		Map<String, Double> coords = null;
		List<String> libraryIds = librariesRepository.getUserLibIds(userId, isOwner, logged);
		Map<String,Long> topTags = copiesRepository.getTags(libraryIds);

		if((!isOwner && visibility.equals(VisibilityOptions.PRIVATE.toDbValue()))|| (visibility.equals(VisibilityOptions.LOGGED_IN.toDbValue()) && !logged)) {
			userName = "utente con profilo privato";
		}else if (user.getLocationId() != null) {
			coords = locationService.getLocationMap(user.getLocationId());
		}


		return new UserProfileDTO(
				userId,
				userName,
				topTags,
				visibility,
				coords,
				blurRadius,
				libraryIds
				);
	}


	public DeletionResult tryFullDeleteUser(String userId) {

		// Cerca prestiti attivi (ON_LOAN) dove l'utente è coinvolto
		List<Loan> activeLoans = loansRepository.findActiveByUser(userId);

		if (!activeLoans.isEmpty()) {
			// L'utente ha dei libri fuori o deve riceverne indietro
			return new DeletionResult(false, activeLoans);
		}

		// Se non ci sono prestiti attivi, puliamo le richieste PENDING
		loansRepository.deletePendingByUserId(userId);

		// cancellazione totale
		fullDeleteUser(userId);

		return new DeletionResult(true, null);
	}

	private void fullDeleteUser(String userId) {

		LOG.infof("Avvio eliminazione a cascata per utente: %s", userId);

		// trova tutte le librerie dell'utente
		List<Library> userLibraries = librariesRepository.getAll(userId).into(new ArrayList<>());

		for (Library lib : userLibraries) {
			// per ogni libreria, elimina tutte le copie dei libri contenute
			copiesRepository.deleteByLibraryId(lib.getId());
			// elimina la libreria stessa
			librariesRepository.delete(lib.getId());
		}

		// elimina utente
		userRepository.delete(userId);

		LOG.infof("Eliminazione a cascata completata per utente: %s", userId);

	}
	
	public Map<String, Object> collectFullUserData(User user) {
	    LOG.debug("collectFullUserData init");
	    Map<String, Object> data = new HashMap<>();
	    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

	    // 1. DATI PROFILO
	    Map<String, Object> userData = new HashMap<>();
	    userData.put("username", user.getUsername());
	    userData.put("email", user.getEmail());
	    userData.put("visibility", user.getVisibility());
	    userData.put("createdAt", user.getCreatedAt() != null ? sdf.format(user.getCreatedAt()) : "N/D");
	    userData.put("modifiedAt", user.getModifiedAt() != null ? sdf.format(user.getModifiedAt()) : "N/D");
	    userData.put("blurRadius", user.getBlurRadius() + " metri");
	    userData.put("history", user.getHistory()); // Aggiungiamo la history qui

	    // Location Profilo (Mappata con le chiavi cercate dal PDF)
	    if (user.getLocationId() != null) {
	        Map<String, Double> coords = locationService.getLocationMap(user.getLocationId());
	        if (coords != null) {
	            userData.put("location", Map.of(
	                "latitude", coords.getOrDefault("lat", 0.0), 
	                "longitude", coords.getOrDefault("lon", 0.0)
	            ));
	        }
	    }
	    data.put("user", userData);

	    // 2. LIBRERIE E LIBRI
	    List<Map<String, Object>> libsData = new ArrayList<>();
	    librariesRepository.getAll(user.getId()).forEach(lib -> {
	        Map<String, Object> libMap = new HashMap<>();
	        libMap.put("name", lib.getName());
	        libMap.put("createdAt", lib.getCreatedAt() != null ? sdf.format(lib.getCreatedAt()) : "N/D");
	        
	        // Location Libreria
	        if (lib.getLocationId() != null) {
	            Map<String, Double> lCoords = locationService.getLocationMap(lib.getLocationId());
	            if (lCoords != null) {
	                libMap.put("location", Map.of(
	                    "latitude", lCoords.getOrDefault("lat", 0.0), 
	                    "longitude", lCoords.getOrDefault("lon", 0.0)
	                ));
	            }
	        }

	        List<Map<String, Object>> booksInfo = new ArrayList<>();
	        copiesRepository.findByLibrary(lib.getId()).forEach(copy -> {
	            Optional<Book> bookOpt = booksRepository.get(copy.getBookIsbn());
	            Map<String, Object> b = new HashMap<>();
	            if (bookOpt.isPresent()) {
	                Book book = bookOpt.get();
	                b.put("title", book.getTitle());
	                b.put("author", book.getAuthor());
	                b.put("year", book.getPublication_year());
	                b.put("publisher", book.getPublisher());
	            } else {
	                b.put("title", "ISBN: " + copy.getBookIsbn());
	                b.put("author", "N/D");
	                b.put("year", 0);
	                b.put("publisher", "N/D");
	            }
	            b.put("status", copy.getStatus());
	            b.put("condition", copy.getCondition());
	            booksInfo.add(b);
	        });
	        libMap.put("books", booksInfo);
	        libsData.add(libMap);
	    });
	    data.put("libraries", libsData);

	    // 3. PRESTITI
	    List<Map<String, Object>> received = new ArrayList<>();
	    List<Map<String, Object>> made = new ArrayList<>();

	    loansRepository.findAllUserLoans(user.getId()).forEach(loan -> {
	        Map<String, Object> l = new HashMap<>();
	        l.put("title", loan.getTitle());
	        l.put("status", loan.getStatus());
	        l.put("requestedAt", loan.getCreatedAt() != null ? sdf.format(loan.getCreatedAt()) : "N/D");
	        l.put("expectedReturn", loan.getExpectedReturnDate() != null ? sdf.format(loan.getExpectedReturnDate()) : "-");

	        String partnerId = loan.getOwnerId().equals(user.getId()) ? loan.getRequesterId() : loan.getOwnerId();
	        Optional<User> p = userRepository.get(partnerId);
	        l.put("partner", p.isPresent() ? p.get().getUsername() : "Utente Privato");

	        if (loan.getOwnerId().equals(user.getId())) received.add(l);
	        else made.add(l);
	    });

	    data.put("loansReceived", received);
	    data.put("loansMade", made);
	    
	    return data;
	}
	
	// Classe di supporto per il risultato
	public static class DeletionResult {
		public boolean success;
		public List<Loan> blockingLoans;
		public DeletionResult(boolean success, List<Loan> blockingLoans) {
			this.success = success;
			this.blockingLoans = blockingLoans;
		}
	}

}