package it.unipegaso.api.resources;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per ricerca collezioni/libri con filtri geospaziali e testuali.
 */
@Path("/api/collections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectionResource {

    /**
     * GET /api/collections
     * Ricerca con multipli filtri opzionali.
     * 
     * Query params:
     * - near: lat,lon (es: "41.9028,12.4964")
     * - radius: metri (default: 5000)
     * - bbox: lon1,lat1,lon2,lat2
     * - tags: "storia,arte"
     * - q: ricerca testuale titolo/autore
     */
    @GET
    public Response searchCollections(
            @QueryParam("near") String near,
            @QueryParam("radius") @DefaultValue("5000") int radius,
            @QueryParam("bbox") String bbox,
            @QueryParam("tags") String tags,
            @QueryParam("q") String textQuery,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        
        // TODO: costruire query MongoDB con:
        // - $nearSphere se near presente
        // - $geoWithin se bbox presente
        // - $text se q presente
        // - match su tags se presente
        // TODO: filtrare utenti con locationMode=none
        
        return Response.ok("{\"results\": [], \"total\": 0, \"message\": \"TODO - geospatial query\"}").build();
    }

    /**
     * POST /api/collections
     * Crea nuova collezione con libri e posizione.
     * Auth: user autenticato
     */
    @POST
    @RolesAllowed("user")
    public Response createCollection(Object collectionDto) {
        // TODO: validare location + salvare con privacy mode utente
        return Response.status(Response.Status.CREATED)
                .entity("{\"id\": \"col-123\", \"message\": \"Collection created (TODO)\"}")
                .build();
    }

    /**
     * GET /api/collections/{id}
     * Dettaglio collezione con lista libri.
     */
    @GET
    @Path("/{id}")
    public Response getCollection(@PathParam("id") String collectionId) {
        // TODO: fetch collezione + populate items
        return Response.ok("{\"id\": \"" + collectionId + "\", \"title\": \"TODO\"}").build();
    }

    /**
     * PUT /api/collections/{id}
     * Aggiorna collezione (solo owner).
     */
    @PUT
    @Path("/{id}")
    @RolesAllowed("user")
    public Response updateCollection(
            @PathParam("id") String collectionId,
            Object collectionDto) {
        // TODO: verificare ownership + aggiornare
        return Response.ok("{\"message\": \"Collection updated (TODO)\"}").build();
    }

    /**
     * DELETE /api/collections/{id}
     * Elimina collezione (solo owner, non elimina i libri).
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("user")
    public Response deleteCollection(@PathParam("id") String collectionId) {
        // TODO: verificare ownership + delete
        return Response.noContent().build();
    }
}
