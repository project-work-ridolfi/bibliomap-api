package it.unipegaso.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
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
            // Ritorna 500 Internal Server Error (incoerenza del DB)
			throw new NotFoundException(
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					    .entity(new ErrorResponse("USER_NOT_FOUND", "Utente registrato non trovato nel database."))
					    .build()
            );
		}

		// Successo
		return userOpt.get();
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