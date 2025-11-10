package it.unipegaso.api.resources;


import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per arricchimento metadati da ISBN.
 */
@Path("/api/isbn")
@Produces(MediaType.APPLICATION_JSON)
public class IsbnResource {

    /**
     * GET /api/isbn/{code}
     * Recupera metadati libro da Google Books API.
     * Response: { "title": "...", "authors": [...], "year": 2020, "thumbnail": "url" }
     */
    @GET
    @Path("/{code}")
    @RolesAllowed("user")
    public Response getBookByIsbn(@PathParam("code") String isbn) {
        // TODO: chiamata a https://www.googleapis.com/books/v1/volumes?q=isbn:{code}
        // TODO: parsare response + ritornare DTO normalizzato
        return Response.ok("{\"title\": \"TODO - fetch from Google Books\", \"isbn\": \"" + isbn + "\"}").build();
    }
}