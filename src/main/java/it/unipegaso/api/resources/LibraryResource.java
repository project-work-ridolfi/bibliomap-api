package it.unipegaso.api.resources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.LibraryDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.Location;
import it.unipegaso.database.model.User;
import it.unipegaso.service.BookService;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.LocationService;
import it.unipegaso.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
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

	@Inject
	BookService bookService;

	@Inject
	UsersRepository usersRepository;
	
	@Inject
	CopiesRepository copiesRepository;

	@Inject 
	LibrariesRepository librariesRepository;
	
	@Inject
	LocationService locationService;
	
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
		}catch(NoSuchElementException e) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ErrorResponse("BAD_REQUEST", "visibility non valida"))
					.build();
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



	@GET
	@Path("/{id}")
	public Response getLibrary(@PathParam("id") String libraryId, @Context HttpHeaders headers) {

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
		String currentUserId = "";

		try {
			User currentUser = userService.getUserFromSession(sessionId);
			currentUserId = currentUser.getId();
		} catch (Exception e) {
			// utente non loggato, procediamo come guest (currentUserId resta null)
		}

		Library library = libraryService.getLibraryDetail(libraryId, currentUserId);

		if (library == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		List<BookDetailDTO> books = bookService.getBooksByLibrary(libraryId, 0.0);

		// costruisco la risposta includendo i libri e la posizione
		Map<String, Object> response = new HashMap<>();

		response.put("id", library.getId());
		response.put("name", library.getName());
		response.put("visibility", library.getVisibility());
		response.put("books", books);
		response.put("views", library.getViewsCounter());
		
		Map<String, Double> cords = locationService.getLocationMap(library.getLocationId());
		
		if(cords != null && !cords.isEmpty()) {
			response.putAll(cords);
		}
		

		Optional<User> opOwner = usersRepository.get(library.getOwnerId());

		if(opOwner.isPresent()) {
			User owner = opOwner.get();
			if(!owner.getVisibility().equals("private") || owner.getId().equals(currentUserId)) {
				response.put("ownerName", owner.getUsername());
				response.put("ownerId", owner.getId());
			}
		}

		return Response.ok(response).build();
	}
	
	
	@PUT
	@Path("/{id}")
	public Response updateLibrary(@PathParam("id") String libraryId, @Context HttpHeaders headers, LibraryDTO request) {
		
		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User user = userService.getUserFromSession(sessionId); 

			String userId = user.getId();
			
			Library library = libraryService.getLibraryDetail(libraryId, userId);

			if (library == null) {
				LOG.info("LIBRERIA DA MODIFICARE NON TROVATA");
				return Response.status(Response.Status.NOT_FOUND).build();
			}
			
			if(!userId.equals(library.getOwnerId())) {
				//non si dovrebbe mai finire qui, ma per essere certi al 100%
				return Response.status(Response.Status.FORBIDDEN).entity(new ErrorResponse("ACTION FORBIDDEN", "l'utente non e' il proprietario della libreria")).build();
			}
			
			//se riusciamo a modificare
			if(libraryService.updateLibrary(request, library)) {
				return Response.noContent().build();
			}
			else {
				return Response.serverError().build();
			}

		}catch(NotAuthorizedException e) {
			return e.getResponse();
		}catch (WebApplicationException e) {
			return e.getResponse();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}
		
	}


	/**
	 * DELETE /api/libraries/{id}
	 * Elimina collezione (solo owner, non elimina i libri ma le copie).
	 */
	@DELETE
	@Path("/{id}")
	public Response deleteLibrary(@PathParam("id") String libraryId, @Context HttpHeaders headers) {
		
		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User user = userService.getUserFromSession(sessionId); 

			String userId = user.getId();
			
			Library library = libraryService.getLibraryDetail(libraryId, userId);

			if (library == null) {
				LOG.info("LIBRERIA DA ELIMINARE NON TROVATA");
				return Response.noContent().build();
			}
			
			if(!userId.equals(library.getOwnerId())) {
				//non si dovrebbe mai finire qui, ma per essere certi al 100%
				return Response.status(Response.Status.FORBIDDEN).entity(new ErrorResponse("ACTION FORBIDDEN", "l'utente non e' il proprietario della libreria")).build();
			}

			//cancelliamo tutte le copie con quel library id
			copiesRepository.deleteByLibraryId(libraryId);
			// elimina la libreria stessa
			librariesRepository.delete(libraryId);

			return Response.noContent().build();
		}catch(NotAuthorizedException e) {
			return e.getResponse();
		}catch (WebApplicationException e) {
			return e.getResponse();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}
	}
}