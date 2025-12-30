package it.unipegaso.api.resources;

import java.util.HashMap;
import java.util.Map;

import it.unipegaso.api.dto.UserStatsDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.User;
import it.unipegaso.service.LibraryService;
import it.unipegaso.service.StatsService;
import it.unipegaso.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per statistiche utente.
 */
@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

	@Inject
	BooksRepository bookRepository;
	
	@Inject 
	UserService userService;
	
	@Inject
	LibraryService libraryService;
	
	@Inject
	LoansRepository loansRepository;
	
	@Inject
	UsersRepository usersRepository;
	
	@Inject
	StatsService statsService;

	
	@GET
    @Path("/user/{id}")
    public Response getUserStats(@PathParam("id") String userId, @Context HttpHeaders headers) {
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
        try {
            User currentUser = userService.getUserFromSession(sessionId);
            
            // verifica che l'utente chieda le proprie statistiche
            if (!currentUser.getId().equals(userId)) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            // delega il calcolo complesso al service
            UserStatsDTO stats = statsService.getAllUserStats(userId);
            return Response.ok(stats).build();

        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
    
    @GET
	@Path("/total")
	public Response getTotalBooks( @Context HttpHeaders headers){
		
		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
		String currentUserId = "*";
		boolean logged = false;
		try {
			User currentUser = userService.getUserFromSession(sessionId);
			currentUserId = currentUser.getId();
			logged = true;
			
		} catch (Exception e) {
			// utente non loggato, procediamo come guest (currentUserId resta null), non si vedranno i libri con visibilita' limitata
		}
		
		//riprendo totale libri
		long totalBooks = bookRepository.count();
		
		long totalCopies = libraryService.countCopies(currentUserId, logged);
		
		long totalLoans = loansRepository.count();
		
		long totalUsers = usersRepository.count(currentUserId,logged);
		
		Map<String, String> response = new HashMap<>();
		response.put("books", "" + totalBooks);
		response.put("copies", "" + totalCopies);
		response.put("loans", "" + totalLoans);
		response.put("users", "" + totalUsers);

		return Response.status(Response.Status.OK).entity(response).build();
		
	}
    
    
}