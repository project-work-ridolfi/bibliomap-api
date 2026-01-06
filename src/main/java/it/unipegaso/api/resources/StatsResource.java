package it.unipegaso.api.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.User;
import it.unipegaso.database.model.VisibilityOptions;
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
	StatsService statsService;

	@Inject 
	UserService userService;

	@Inject 
	BooksRepository bookRepository;

	@Inject
	LibraryService libraryService;

	@Inject 
	LoansRepository loansRepository;

	@Inject 
	UsersRepository usersRepository;

	@GET
	@Path("/user/{id}/counters")
	public Response getUserCounters(@PathParam("id") String userId, @Context HttpHeaders headers) {

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User user = userService.getUserFromSession(sessionId);

			boolean isProfileOwner = user.getId().equals(userId);

			//carichiamo utente se non e' lo stesso e controlliamo visibilita' del profilo
			if(!isProfileOwner && !canSeeProfile(userId)) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			Map<String, Long> counts = new HashMap<>();
			counts.put("myBooksCount", libraryService.countUserCopies(userId, true, isProfileOwner));
			counts.put("totalLoansOut", loansRepository.count(userId, true));
			counts.put("totalLoansIn", loansRepository.count(userId, false));
			return Response.ok(counts).build();

		} catch (Exception e) { 
			return Response.status(Response.Status.UNAUTHORIZED).build(); 
		}
	}

	@GET
	@Path("/user/{id}/full")
	public Response getUserStatsFull(@PathParam("id") String userId, @Context HttpHeaders headers) {

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		try {
			User user = userService.getUserFromSession(sessionId);
			boolean isProfileOwner = user.getId().equals(userId);

			//carichiamo utente se non e' lo stesso e controlliamo visibilita' del profilo
			if(!isProfileOwner && !canSeeProfile(userId)) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			return Response.ok(statsService.getAllUserStats(userId, true, isProfileOwner)).build();

		} catch (Exception e) { 
			return Response.status(Response.Status.UNAUTHORIZED).build(); 
		}
	}

	@GET
	@Path("/global/counters")
	public Response getGlobalCounters() {

		Map<String, Long> response = new HashMap<>();
		response.put("books", bookRepository.count());
		response.put("copies", libraryService.countCopies(null, false));
		response.put("loans", loansRepository.count());
		return Response.ok(response).build();

	}

	@GET
	@Path("/global/full")
	public Response getGlobalStatsFull(@Context HttpHeaders headers) {

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
		String userId = null;
		boolean logged = false;

		try {
			User currentUser = userService.getUserFromSession(sessionId);
			userId = currentUser.getId();
			logged = true;
		} catch (Exception e) {
			// utente non loggato, procediamo come guest (currentUserId resta null)
		}
		return Response.ok(statsService.getGlobalStats(logged, userId)).build(); 
	}


	private boolean canSeeProfile(String userId) {

		Optional<User> opProfileUser = usersRepository.get(userId);

		//anche se e' privato ritorno un not found per proteggere privacy (altro confermerebbe l'esistenza)
		if(opProfileUser.isEmpty() || opProfileUser.get().getVisibility().equals(VisibilityOptions.PRIVATE.toDbValue())) {
			return false;
		}

		return true;
	}
}