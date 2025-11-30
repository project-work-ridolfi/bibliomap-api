package it.unipegaso.api.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/api/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoanRequestResource {

   
    @POST
    @RolesAllowed("user")
    public Response createRequest(Object requestDto) {
        // TODO: validare targetItemId esiste
        // TODO: rate limit (max 10 richieste/giorno per utente)
        // TODO: salvare con status=pending
        // TODO: (opzionale) inviare notifica a proprietario
        return Response.status(Response.Status.CREATED)
                .entity("{\"id\": \"req-123\", \"status\": \"pending\"}").build();
    }

   
    @GET
    @Path("/my")
    @RolesAllowed("user")
    public Response getMyRequests(
            @QueryParam("type") String type,
            @QueryParam("status") String status) {
        // TODO: fetch richieste dove userId = requester OR ownerId
        return Response.ok("{\"sent\": [], \"received\": [], \"message\": \"TODO\"}").build();
    }

    
    @GET
    @Path("/{id}")
    @RolesAllowed("user")
    public Response getRequest(@PathParam("id") String requestId) {
        // TODO: verificare userId = requester OR ownerId
        return Response.ok("{\"id\": \"" + requestId + "\", \"status\": \"TODO\"}").build();
    }

    @PATCH
    @Path("/{id}")
    @RolesAllowed("user")
    public Response updateRequestStatus(
            @PathParam("id") String requestId,
            Object statusUpdateDto) {
        // TODO: validare transizione stato valida
        // TODO: verificare autorizzazioni (owner vs requester)
        // TODO: aggiornare + audit log
        return Response.ok("{\"id\": \"" + requestId + "\", \"status\": \"TODO\"}").build();
    }
}
