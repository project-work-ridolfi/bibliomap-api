package it.unipegaso.service;

import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.database.UsersRepository;
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

    /**
     * Recupera l'oggetto User dal database utilizzando la Session ID e i dati in Redis.
     * @param sessionId L'ID della sessione utente (dal cookie SESSION_ID).
     * @return L'oggetto User se trovato.
     * @throws NotAuthorizedException se la sessione o i dati di autenticazione sono mancanti.
     * @throws NotFoundException se l'utente è in sessione ma non nel DB.
     */
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
}