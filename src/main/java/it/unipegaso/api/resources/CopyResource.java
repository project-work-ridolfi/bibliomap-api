package it.unipegaso.api.resources;

import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.model.Copy;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.User;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/copies")
@Produces(MediaType.APPLICATION_JSON)
public class CopyResource {
	
	private static final Logger LOG = Logger.getLogger(CopyResource.class);

	@Inject
	CopiesRepository copiesRepository;
	
	@Inject 
	UserService userService;
	
	@Inject
	LibraryService libraryService;
	
	@DELETE
	@Path("/{id}")
	public Response deleteCopy(@PathParam("id") String copyId, @Context HttpHeaders headers) {
		
		LOG.debug("DELETE COPY " + copyId);
		
		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			//prendo l'utente
			User user = userService.getUserFromSession(sessionId);

			//prendo la copia del libro
			Optional<Copy> opCopy = copiesRepository.get(copyId);	
			
			// se non la trovo ritorno subito 200
			if(opCopy.isEmpty()) {
				LOG.info("return 200, copy not found");
				return Response.status(Response.Status.ACCEPTED).build();
			}
			
			Copy copy = opCopy.get();
			String copyLibraryId = copy.libraryId;
			
			//prendo tutte le sue librerie
			List<Library> libraries = libraryService.getUserLibraries(user.id);
			
			//controllo che fra la lista delle librerie dell'utente ci sia quella della copia da cancellare
			boolean found = false;
			
			for(Library library:libraries) {
				
				String libraryId = library.id;
				
				if(copyLibraryId.equals(libraryId)) {
					found = true;
				}
				
			}
			
			// la copia non appartiene all'utente, errore
			if(!found) {
				return Response.status(Response.Status.FORBIDDEN)
						.entity(new ErrorResponse("USER_ERROR", "l'utente non possiede la copia"))
						.build();
			}
			
			boolean canceled = copiesRepository.delete(copyLibraryId);
			
			// se trovato e cancellato
			if(canceled) {
				LOG.info("return 200");
				return Response.status(Response.Status.ACCEPTED).build();
			}

			// 500 db error
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ErrorResponse("DB_ERROR", "fallimento eliminazione copy dal db"))
					.build();
		}catch(NotAuthorizedException e) {
			return e.getResponse();
		} catch (IllegalStateException e) {
			LOG.errorf("errore logica: %s", e.getMessage());
			return Response.status(Response.Status.UNAUTHORIZED) // 401 se l'utente non è valido
					.entity(new ErrorResponse("AUTH_ERROR", "utente non autenticato o non trovato"))
					.build();
		} catch (Exception e) {
			LOG.error("errore sconosciuto creazione libreria", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ErrorResponse("SERVER_ERROR", "errore interno creazione libreria"))
					.build();
		}
		
		
	}

}
