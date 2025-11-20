package it.unipegaso.api.resources;

import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import org.eclipse.microprofile.jwt.JsonWebToken;
import it.unipegaso.api.dto.CheckExistsResponse;
import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.SetLocationDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.User;
import it.unipegaso.service.LocationService;
import it.unipegaso.service.SessionDataService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

	private static final Logger LOG = Logger.getLogger(UserResource.class); 

	@Inject
	UsersRepository userRepository;

	@Inject
	LocationService locationService;
	
	@Inject
	SessionDataService sessionDataService;
	
	@Inject
    JsonWebToken jwt;
	
	/**
	 * GET /api/users/check-exists/{username}
	 * Fa un check per vedere se l'username è già registrato (esiste) 
	 * interrogando il database MongoDB.
	 */
	@GET
	@Path("/check-exists/{username}")
	@PermitAll 
	public Response checkUsernameExists(@PathParam("username") String username) {

	    boolean exists = userRepository.findByUsername(username).isPresent();

	    LOG.infof("DB Check username '%s' (Esiste: %b)", username, exists);

	    return Response.ok(new CheckExistsResponse(exists)).build();
	}
	
	/**
	 * GET /api/users/{id}
	 * Recupera profilo pubblico (filtra campi privacy).
	 */
	@GET
	@Path("/{id}")
	public Response getUserProfile(@PathParam("id") String userId) {
		// TODO: fetch user + filtrare campi sensibili in base privacy mode
		return Response.ok("{\"username\": \"user" + userId + "\", \"displayName\": \"TODO\"}").build();
	}

	
	@POST
	@Path("/set-location")
	public Response setLocation(SetLocationDTO request, @Context HttpHeaders headers) {
		
        Response errorResponse = null; // variabile temporanea per gli errori
        String locationId = null;

        //RECUPERO SESSION ID E USERNAME
        String sessionId = SessionIDProvider.getSessionId(headers).orElse("");
        
        if(sessionId.isEmpty()) {
        	errorResponse = Response.status(Response.Status.UNAUTHORIZED)
        			.entity(new ErrorResponse("SESSION_EXPIRED", "Sessione utente scaduta o mancante."))
        			.build();
        }
        
        // Tentativo di recuperare l'username salvato durante la registrazione
        String username = sessionDataService.get(sessionId, "username").orElse(null);
        
        if(username == null && errorResponse == null) {
            errorResponse = Response.status(Response.Status.UNAUTHORIZED)
        			.entity(new ErrorResponse("SESSION_DATA_MISSING", "Dati username mancanti nella sessione."))
        			.build();
        }
        
        // Se c'è stato un errore 
        if(errorResponse != null) {
            LOG.errorf("Blocco setLocation per errore sessione/username: %s", username);
            return errorResponse;
        }

        // RECUPERO UTENTE COMPLETO 
        Optional<User> userOpt = userRepository.findByUsername(username);
		
		if(userOpt.isEmpty()) {
			 LOG.errorf("Impossibile trovare User DB tramite username: %s", username);
             return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                   .entity(new ErrorResponse("USER_NOT_FOUND", "Utente registrato non trovato nel database."))
                   .build();
		}
		
		User user = userOpt.get(); // L'oggetto User da aggiornare
        
        // SALVATAGGIO NUOVA LOCATION
		try {
		    locationId = locationService.saveNewLocation(request); 

		    if(locationId == null) {
		        LOG.error("Salvataggio location fallito: Location ID non generato.");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                              .entity(new ErrorResponse("DB_FAILURE", "Creazione location fallita (ID nullo)."))
                              .build();
		    }
		} catch (RuntimeException e) {
		    LOG.errorf(e, "Errore durante il salvataggio della location per %s.", username);
             return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                              .entity(new ErrorResponse("DB_FAILURE", "Errore DB durante salvataggio location."))
                              .build();
		}
		
		// AGGIORNAMENTO UTENTE CON IL LOCATION ID E I SETTING
		user.locationId = locationId;
		user.visibility = request.visibility();
		user.blurRadius = request.blurRadius();
		
		boolean updateSuccess = userRepository.updateUser(user);

		if(updateSuccess) {
		    LOG.infof("Posizione salvata e Utente %s aggiornato con Location ID: %s", user.username, locationId);
		    // Successo 204 No Content
		    return Response.ok().build(); 
		} else {
		    LOG.errorf("Fallimento nell'aggiornare User %s con Location ID %s", user.username, locationId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                              .entity(new ErrorResponse("DB_FAILURE", "Aggiornamento utente con locationId fallito."))
                              .build();
		}
	}

	
	/**
     * GET /api/users/me
     * Recupera il profilo dell'utente autenticato (owner).
     * Auth: Richiede un token JWT valido (tramite Cookie HttpOnly).
     */
	@GET
    @Path("/me")
    @RolesAllowed("user") 
    public Response getUserMe() {
        
        // estrae l'id utente dal jwt (il 'sub' o 'upn')
        String userId = jwt.getName(); 
        
        if (userId == null) {
            // errore se il token non ha un subject valido 
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity(new ErrorResponse("auth_missing", "token jwt mancante o malformato."))
                           .build();
        }

        LOG.infof("tentativo di recupero profilo per user id: %s", userId);
        
        // recupera l'utente completo dal database
        Optional<User> userOpt = userRepository.get(userId); 
        
        if (userOpt.isEmpty()) {
            LOG.errorf("user id trovato nel jwt ma non nel db: %s", userId);
            // forza il logout nel fe in quanto la sessione è inconsistente
            return Response.status(Response.Status.UNAUTHORIZED) 
                           .entity(new ErrorResponse("user_inconsistent", "utente non trovato nel db, sessione inconsistente."))
                           .build();
        }

        User user = userOpt.get();
        
        // mappa l'oggetto user a un dto sicuro per il frontend (solo id, username, email)
        Map<String, String> responseBody = Map.of(
            "id", user.id, 
            "username", user.username,
            "email", user.email 
        );
        
        LOG.infof("profilo caricato con successo per: %s", user.username);

        return Response.ok(responseBody).build();
    }

	/**
	 * PUT /api/users/{id}/privacy
	 * Body: { "locationMode": "pin", "shareEmail": false }
	 * Auth: solo owner può modificare
	 */
	@PUT
	@Path("/{id}/privacy")
	@RolesAllowed("user")
	public Response updatePrivacySettings(
			@PathParam("id") String userId,
			Object privacyDto) {
		// TODO: validare locationMode (exact|pin|city|none)
		// TODO: salvare preferenze + audit log
		return Response.ok("{\"message\": \"Privacy updated (TODO)\"}").build();
	}

	/**
	 * GET /api/user/export
	 * Esporta tutti i dati personali (GDPR compliance).
	 * Auth: solo owner
	 */
	@GET
	@Path("/export")
	@RolesAllowed("user")
	public Response exportUserData() {
		// TODO: generare JSON con tutti i dati utente
		// (profilo, libri, collezioni, richieste, stats)
		return Response.ok("{\"export\": \"TODO - complete user data\"}").build();
	}

	/**
	 * DELETE /api/user
	 * Cancellazione account (right to be forgotten).
	 * Auth: solo owner
	 */
	@DELETE
	@RolesAllowed("user")
	public Response deleteAccount() {
		// TODO: hard delete + cascade (items, collections, requests)
		// TODO: anonimizzare stats aggregate
		return Response.noContent().build();
	}
}