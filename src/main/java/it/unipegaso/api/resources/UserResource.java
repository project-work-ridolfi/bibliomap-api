package it.unipegaso.api.resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.CheckExistsResponse;
import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.SetLocationDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.User;
import it.unipegaso.service.EmailService;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.LocationService;
import it.unipegaso.service.SessionDataService;
import it.unipegaso.service.UserService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
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
	UserService userService;

	@Inject
	LocationService locationService;

	@Inject
	SessionDataService sessionDataService;
	
	@Inject
	LibraryService libraryService;
	
	@Inject
	EmailService emailService;


	@GET
	@Path("/check-exists/{username}")
	@PermitAll 
	public Response checkUsernameExists(@PathParam("username") String username) {

		boolean exists = userRepository.findByUsername(username).isPresent();

		LOG.infof("DB Check username '%s' (Esiste: %b)", username, exists);

		return Response.ok(new CheckExistsResponse(exists)).build();
	}
	
	@PUT
	@Path("/{id}/privacy")
	public Response updatePrivacySettings(@PathParam("id") String userId, Map<String, Object> data, @Context HttpHeaders headers) {
	    String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
	    try {
	        User user = userService.getUserFromSession(sessionId);
	        
	        // verifica che l'utente modifichi se stesso
	        if (!user.getId().equals(userId)) {
	            return Response.status(Response.Status.FORBIDDEN).build();
	        }

	        if (data.containsKey("username")) {
	        	user.setUsername((String) data.get("username"));
	        }
	        
	        if (data.containsKey("visibility")) {
	        	user.setVisibility((String) data.get("visibility"));
	        }
	        
	        if (data.containsKey("blurRadius")) {
	            Number blur = (Number) data.get("blurRadius");
	            user.setBlurRadius(blur.intValue());
	        }

	        userRepository.update(user);
	        return Response.ok(user).build();
	    } catch (Exception e) {
	        return Response.status(Response.Status.UNAUTHORIZED).build();
	    }
	}

	
	@GET
	@Path("/{id}")
	public Response getUserProfile(@PathParam("id") String userId) {
		// TODO: fetch user + filtrare campi sensibili in base privacy mode
		return Response.ok("{\"username\": \"user" + userId + "\", \"displayName\": \"TODO\"}").build();
	}


	@POST
	@Path("/set-location")
	public Response setLocation(SetLocationDTO request, @Context HttpHeaders headers) {

		String locationId = null;

		//RECUPERO SESSION ID E USERNAME
		String sessionId = SessionIDProvider.getSessionId(headers).orElse("");
		User user = null;

		try {
			user = userService.getUserFromSession(sessionId);

			// SALVATAGGIO NUOVA LOCATION
			locationId = locationService.saveNewLocation(request); 

			if(locationId == null) {
				LOG.error("Salvataggio location fallito: Location ID non generato.");
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ErrorResponse("DB_FAILURE", "Creazione location fallita (ID nullo)."))
						.build();
			}
		} catch (NotAuthorizedException | NotFoundException e) {
			return e.getResponse();
		}

		// AGGIORNAMENTO UTENTE CON IL LOCATION ID E I SETTING
		user.setLocationId(locationId);
		user.setVisibility(request.visibility());
		user.setBlurRadius(request.blurRadius());

		boolean updateSuccess = userRepository.update(user);

		if(updateSuccess) {
			LOG.infof("Posizione salvata e Utente %s aggiornato con Location ID: %s", user.getUsername(), locationId);
			// Successo 204 No Content
			return Response.ok().build(); 
		} else {
			LOG.errorf("Fallimento nell'aggiornare User %s con Location ID %s", user.getUsername(), locationId);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ErrorResponse("DB_FAILURE", "Aggiornamento utente con locationId fallito."))
					.build();
		}
	}


	@GET
	@Path("/me")
	public Response getUserMe(@Context HttpHeaders headers) {

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User user = userService.getUserFromSession(sessionId);

			// se il controllo arriva qui senza eccezioni, l'utente e' autenticato 
			Map<String, Object> responseBody = new HashMap<>();
	        responseBody.put("id", user.getId());
	        responseBody.put("username", user.getUsername());
	        responseBody.put("email", user.getEmail());
	        responseBody.put("visibility", user.getVisibility()); 
	        responseBody.put("blurRadius", user.getBlurRadius()); 

			LOG.infof("profilo caricato con successo per: %s", user.getUsername());
			return Response.ok(responseBody).build();

		} catch (NotAuthorizedException e) {
			// Cattura 401/403 lanciati dal servizio.
			return e.getResponse(); 
		} catch (NotFoundException e) {
			// Cattura 500 lanciati dal servizio per errore DB
			return e.getResponse(); 
		}
	}


	@GET
	@Path("/me/libraries")
	public Response getUserLibraries(@Context HttpHeaders headers) {

	    LOG.info("GET LIBRARIES");

	    String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

	    try {
	        User user = userService.getUserFromSession(sessionId); 

	        String userId = user.getId();

	        List<Library> userLibraries = libraryService.getUserLibraries(userId); 
	        
	        List<Map<String, String>> librariesDTO = new ArrayList<>();
	        
	        for(Library lib : userLibraries) {
	        	
	        	Map<String, String> namesIdsMap = new HashMap<>();
	        	namesIdsMap.put("id", lib.getId());
	        	namesIdsMap.put("name", lib.getName());
	        	librariesDTO.add(namesIdsMap);
	        	
	        }

	        // Se la lista e' vuota (0 librerie), lo stato è comunque 200 OK
	        return Response.ok(Map.of("libraries", librariesDTO, "count", librariesDTO.size())).build();
	    
	    }catch(NotAuthorizedException e) {
	        // Errore di autenticazione/sessione (401)
	        return e.getResponse();
	    } catch (Exception e) {
	        // Errore grave (es. fallimento del database)
	        LOG.error("errore sconosciuto durante il recupero delle librerie", e);
	        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
	                .entity(new ErrorResponse("SERVER_ERROR", "errore interno durante il recupero delle librerie."))
	                .build();
	    }
	}

	
	@GET
    @Path("/export")
    public Response exportUserData(@Context HttpHeaders headers) {
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
        try {
            User user = userService.getUserFromSession(sessionId);
            
            // in un sistema reale qui si avvierebbe un job asincrono
            // per ora simuliamo l'invio della mail
            LOG.infof("Richiesta export dati per utente: %s", user.getEmail());
            
            // TODO: implementare servizio email sendExportData(user)
            
            return Response.ok(Map.of("message", "La richiesta è stata presa in carico. Riceverai un'email con il dump dei tuoi dati.")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

	@DELETE
	@Path("/me")
	public Response deleteAccount(@Context HttpHeaders headers) {
	    String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
	    try {
	        User user = userService.getUserFromSession(sessionId);
	        
	        UserService.DeletionResult result = userService.tryFullDeleteUser(user.getId());

	        if (result.success) {
	            emailService.sendAccountDeletedEmail(user.getEmail(), user.getUsername());
	            sessionDataService.delete(sessionId);
	            return Response.noContent().build();
	        } else {
	            // Notifichiamo l'utente via email sui prestiti bloccanti
	            emailService.sendDeletionBlockedEmail(user.getEmail(), user.getUsername(), result.blockingLoans);
	            
	            // Ritorniamo un 409 Conflict per dire al frontend che non si può fare
	            return Response.status(Response.Status.CONFLICT)
	                    .entity(Map.of("message", "Prestiti attivi in corso", "loans", result.blockingLoans))
	                    .build();
	        }
	    } catch (Exception e) {
	        return Response.status(Response.Status.UNAUTHORIZED).build();
	    }
	}


}