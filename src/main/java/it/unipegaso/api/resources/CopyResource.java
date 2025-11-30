package it.unipegaso.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.util.ImageUtils;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.model.Copy;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.User;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.UserService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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
			
			boolean canceled = copiesRepository.delete(copyId);
			
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

	
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("user")
    @Path("/add-copy")
    public Response createBook(
            @FormParam("title") String title,
            @FormParam("authors") String authors,
            @FormParam("isbn") String isbn,
            @FormParam("tags") String tags,
            @FormParam("cover") FileUpload cover) {
        // TODO: validare input
        // TODO: se cover presente → upload GridFS + genera thumbnail
        // TODO: salvare item su MongoDB
        return Response.status(Response.Status.CREATED)
                .entity("{\"id\": \"book-123\", \"message\": \"Book created (TODO)\"}")
                .build();
    }
    
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response updateCopy(
            @PathParam("id") String copyId,
            @FormParam("status") String status,
            @FormParam("condition") String condition,
            @FormParam("ownerNotes") String ownerNotes,
            @FormParam("tags") String tags, 
            @FormParam("useDefaultCover") boolean useDefaultCover, 
            @FormParam("coverFile") FileUpload coverFile,
            @Context HttpHeaders headers) {

        LOG.debug("update copy " + copyId);
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

        try {
            User user = userService.getUserFromSession(sessionId);
            Optional<Copy> opCopy = copiesRepository.get(copyId);

            if (opCopy.isEmpty()) return Response.status(Response.Status.NOT_FOUND).build();
            Copy copy = opCopy.get();

            // verify ownership
            boolean isOwner = libraryService.getUserLibraries(user.id).stream()
                    .anyMatch(lib -> lib.id.equals(copy.libraryId));

            if (!isOwner) return Response.status(Response.Status.FORBIDDEN).build();

            // update fields
            copy.status = status;
            copy.condition = condition;
            copy.ownerNotes = ownerNotes;
            
            // update tags
            if (tags != null && !tags.isEmpty()) {
                copy.tags = Arrays.asList(tags.split(","));
            } else {
                copy.tags = new ArrayList<>();
            }

            // handle cover logic
            if (useDefaultCover) {
                copy.customCover = null; 
            } else if (coverFile != null && coverFile.fileName() != null) {
                byte[] fileBytes = java.nio.file.Files.readAllBytes(coverFile.uploadedFile());
                String base64Img = ImageUtils.resizeAndConvertToBase64(fileBytes, 400);
                copy.customCover = "data:image/jpeg;base64," + base64Img;
            }

            boolean updated = copiesRepository.update(copy); 
            
            if(!updated) {
            	LOG.error("IMPOSSIBILE MODIFICARE COPIA");
            	return Response.serverError().entity(new ErrorResponse("ERR", "update failed")).build();
            }
            return Response.ok().build();

        } catch (Exception e) {
            LOG.error("error updating copy", e);
            return Response.serverError().entity(new ErrorResponse("ERR", "update failed")).build();
        }
    }
    
    public static class CopyUpdateForm {
        @FormParam("status") public String status;
        @FormParam("condition") public String condition;
        @FormParam("ownerNotes") public String ownerNotes;
        @FormParam("tags") public String tags; // separate da virgola
        @FormParam("useDefaultCover") public boolean useDefaultCover; 
        @FormParam("coverFile") public FileUpload coverFile; 
    }
}
