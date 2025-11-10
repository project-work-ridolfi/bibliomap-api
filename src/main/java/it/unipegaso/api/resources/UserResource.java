package it.unipegaso.api.resources;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.CheckExistsResponse;
import it.unipegaso.database.UserRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

	private static final Logger LOG = Logger.getLogger(UserResource.class); 



	@Inject
	UserRepository userRepository;

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