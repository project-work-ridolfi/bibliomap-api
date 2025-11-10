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

/**
 * Endpoint per workflow richieste prestito.
 */
@Path("/api/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LoanRequestResource {

    /**
     * POST /api/requests
     * Crea nuova richiesta prestito/consultazione.
     * Body: {
     *   "targetItemId": "book-123",
     *   "type": "loan",
     *   "message": "Vorrei prendere in prestito...",
     *   "proposedDates": ["2025-11-05"],
     *   "duration": 14
     * }
     */
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

    /**
     * GET /api/requests/my
     * Lista richieste utente (inviate + ricevute).
     * Query params:
     * - type: "sent" | "received" (default: all)
     * - status: "pending" | "accepted" | ...
     */
    @GET
    @Path("/my")
    @RolesAllowed("user")
    public Response getMyRequests(
            @QueryParam("type") String type,
            @QueryParam("status") String status) {
        // TODO: fetch richieste dove userId = requester OR ownerId
        return Response.ok("{\"sent\": [], \"received\": [], \"message\": \"TODO\"}").build();
    }

    /**
     * GET /api/requests/{id}
     * Dettaglio richiesta (solo requester o owner).
     */
    @GET
    @Path("/{id}")
    @RolesAllowed("user")
    public Response getRequest(@PathParam("id") String requestId) {
        // TODO: verificare userId = requester OR ownerId
        return Response.ok("{\"id\": \"" + requestId + "\", \"status\": \"TODO\"}").build();
    }

    /**
     * PATCH /api/requests/{id}
     * Aggiorna stato richiesta.
     * Body: { "status": "accepted" | "cancelled" | "completed" }
     * 
     * Autorizzazioni:
     * - Owner può: pending→accepted, pending→cancelled, active→completed
     * - Requester può: pending→cancelled, active→cancelled
     */
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
