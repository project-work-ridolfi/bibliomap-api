package it.unipegaso.api.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per statistiche utente.
 */
@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    /**
     * GET /api/stats/user/{id}
     * Statistiche utente (solo owner o admin).
     * 
     * Response: {
     *   "totalViews": 123,
     *   "totalRequests": 45,
     *   "loansCompleted": 12,
     *   "viewsByMonth": [...],
     *   "topBooks": [...]
     * }
     */
    @GET
    @Path("/user/{id}")
    @RolesAllowed("user")
    public Response getUserStats(@PathParam("id") String userId) {
        // TODO: aggregazioni MongoDB
        // TODO: verificare userId = current user (o admin)
        return Response.ok("{\"totalViews\": 0, \"message\": \"TODO - aggregations\"}").build();
    }

    /**
     * GET /api/stats/global
     * Statistiche aggregate pubbliche (anonimizzate).
     * 
     * Response: {
     *   "totalUsers": 150,
     *   "totalBooks": 1234,
     *   "activeLoans": 23
     * }
     */
    @GET
    @Path("/global")
    public Response getGlobalStats() {
        // TODO: count users, books, active requests
        return Response.ok("{\"totalUsers\": 0, \"totalBooks\": 0}").build();
    }
}