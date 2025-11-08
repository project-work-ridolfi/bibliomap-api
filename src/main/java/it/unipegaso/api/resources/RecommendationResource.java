package it.unipegaso.api.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per raccomandazioni libri.
 */
@Path("/api/books")
@Produces(MediaType.APPLICATION_JSON)
public class RecommendationResource {

    /**
     * GET /api/books/suggest
     * Suggerimenti basati su: distanza → popolarità → autore.
     * 
     * Query params:
     * - lat, lon: posizione utente
     * - radius: raggio ricerca (default: 10km)
     * - limit: max risultati (default: 10)
     */
    @GET
    @Path("/suggest")
    public Response getSuggestions(
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon,
            @QueryParam("radius") @DefaultValue("10000") int radius,
            @QueryParam("limit") @DefaultValue("10") int limit) {
        
        // TODO: algoritmo ranking:
        // 1. Query near con radius
        // 2. Sort per: distanza ASC, popularityScore DESC, author match
        // 3. Limit risultati
        
        return Response.ok("{\"suggestions\": [], \"message\": \"TODO - recommendation engine\"}").build();
    }
}