package it.unipegaso.api.resources;

import java.util.Map;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.LibraryDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.model.User;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/libraries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LibraryResource {

	private static final Logger LOG = Logger.getLogger(LibraryResource.class);

	@Inject
	LibraryService libraryService;

	@Inject 
	UserService userService;

	@POST
	public Response createLibrary(LibraryDTO request, @Context HttpHeaders headers) {

		LOG.info("CREATE LIBRARY ");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User user = userService.getUserFromSession(sessionId);

			String libraryId = libraryService.createNewLibrary(user, request);

			if (libraryId == null) {
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("DB_ERROR", "fallimento creazione db"))
						.build();
			}

			LOG.info("return 201");
			// 201 Created - Ritorna l'ID della nuova libreria
			return Response.status(Response.Status.CREATED)
					.entity(Map.of("libraryId", libraryId, "message", "libreria creata con successo"))
					.build();
		}catch(NotAuthorizedException e) {
			return e.getResponse();
		} catch (IllegalStateException e) {
			LOG.errorf("errore logica: %s", e.getMessage());
			return Response.status(Response.Status.UNAUTHORIZED) // 401 se l'utente non e' valido
					.entity(new ErrorResponse("AUTH_ERROR", "utente non autenticato o non trovato"))
					.build();
		} catch (Exception e) {
			LOG.error("errore sconosciuto creazione libreria", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ErrorResponse("SERVER_ERROR", "errore interno creazione libreria"))
					.build();
		}
	}



	/**
	 * GET /api/libraries/{id}
	 * Dettaglio collezione con lista libri.
	 */
	@GET
	@Path("/{id}")
	public Response getLibrary(@PathParam("id") String libraryId) {
		// TODO: fetch collezione + populate items
		return Response.ok("{\"id\": \"" + libraryId + "\", \"title\": \"TODO\"}").build();
	}

	/**
	 * PUT /api/collections/{id}
	 * Aggiorna collezione (solo owner).
	 */
	@PUT
	@Path("/{id}")
	public Response updateLibrary(
			@PathParam("id") String libraryId,
			Object libraryDto) {
		// TODO: verificare ownership + aggiornare
		return Response.ok("{\"message\": \"Collection updated (TODO)\"}").build();
	}

	/**
	 * DELETE /api/libraries/{id}
	 * Elimina collezione (solo owner, non elimina i libri).
	 */
	@DELETE
	@Path("/{id}")
	public Response deleteLibrary(@PathParam("id") String libraryId) {
		// TODO: verificare ownership + delete
		return Response.noContent().build();
	}
}