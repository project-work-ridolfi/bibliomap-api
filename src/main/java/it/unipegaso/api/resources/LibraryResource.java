package it.unipegaso.api.resources;

import java.util.Map;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.LibraryDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.service.LibraryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/libraries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user") // Protegge l'endpoint TODO admin
public class LibraryResource {
    
    private static final Logger LOG = Logger.getLogger(LibraryResource.class);
    
    @Inject
    LibraryService libraryService;
    
    @POST
    public Response createLibrary(LibraryDTO request, @Context HttpHeaders headers) {
        
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
        
        if (sessionId == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity(new ErrorResponse("SESSION_EXPIRED", "sessione mancante o scaduta"))
                           .build();
        }

        try {
            String libraryId = libraryService.createNewLibrary(sessionId, request);
            
            if (libraryId == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity(new ErrorResponse("DB_ERROR", "fallimento creazione db"))
                               .build();
            }

            // 201 Created - Ritorna l'ID della nuova libreria
            return Response.status(Response.Status.CREATED)
                           .entity(Map.of("libraryId", libraryId, "message", "libreria creata con successo"))
                           .build();

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