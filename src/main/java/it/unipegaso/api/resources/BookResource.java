package it.unipegaso.api.resources;

import java.util.List;

import org.jboss.resteasy.reactive.multipart.FileUpload;

import it.unipegaso.api.dto.BookDetailDTO;
import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.service.BookService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/books")
@Produces(MediaType.APPLICATION_JSON)
public class BookResource {
	
	@Inject
    BookService bookService;
	
	
	@GET
    @Path("/nearby")
    public Response getNearbyBooks(
            @QueryParam("lat") Double lat, 
            @QueryParam("lng") Double lng, 
            @QueryParam("radius") Double radius,
            @QueryParam("visibility") String visibility,
            @QueryParam("exclude_user") String excludeUserId,
            @QueryParam("search") String searchText,
            @QueryParam("sort") String sortBy
    ) {
        
        if (lat == null || lng == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\": \"Coordinate mancanti\"}").build();
        }
        if (radius == null) radius = 10.0; // Aumentiamo un po' il default a 10km
        if (visibility == null) visibility = "public";
        if (sortBy == null) sortBy = "distance"; // Default sorting

        List<BookMapDTO> books = bookService.searchBooks(lat, lng, radius, visibility, excludeUserId, searchText, sortBy);
        
        return Response.ok(books).build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("user")
    @Path("/add-copy")
    public Response createBook(
            @FormParam("title") String title,
            @FormParam("authors") String authors,
            @FormParam("isbn") String isbn,
            @FormParam("tags") String tags,
            @FormParam("cover") FileUpload cover) {
        // TODO: validare input
        // TODO: se cover presente → upload GridFS + genera thumbnail
        // TODO: salvare item su MongoDB
        return Response.status(Response.Status.CREATED)
                .entity("{\"id\": \"book-123\", \"message\": \"Book created (TODO)\"}")
                .build();
    }

    /**
     * GET /api/books/{id}
     * dettaglio copia specifico per pagina view
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBook(@PathParam("id") String copyId) {
        
        BookDetailDTO detail = bookService.getBookDetails(copyId);

        if (detail == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        
        return Response.ok(detail).build();
    }

    /**
     * PUT /api/items/{id}
     * Aggiorna metadati libro (solo owner).
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Response updateBook(
            @PathParam("id") String bookId,
            Object bookDto) {
        // TODO: verificare ownership + aggiornare
        return Response.ok("{\"message\": \"Book updated (TODO)\"}").build();
    }

    /**
     * DELETE /api/items/{id}
     * Elimina libro (solo owner).
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("user")
    public Response deleteBook(@PathParam("id") String bookId) {
        // TODO: verificare ownership + rimuovere da collezioni + delete cover
        return Response.noContent().build();
    }

    /**
     * GET /api/items/{id}/cover
     * Stream copertina full-size (GridFS).
     */
    @GET
    @Path("/{id}/cover")
    @Produces("image/*")
    public Response getBookCover(@PathParam("id") String bookId) {
        // TODO: stream da GridFS
        return Response.ok("TODO: binary image data").build();
    }

    /**
     * GET /api/items/{id}/cover/thumb
     * Thumbnail Base64 inline (veloce).
     */
    @GET
    @Path("/{id}/cover/thumb")
    public Response getBookThumbnail(@PathParam("id") String bookId) {
        // TODO: ritorna { "thumbBase64": "data:image/webp;base64,..." }
        return Response.ok("{\"thumbBase64\": \"TODO\"}").build();
    }

    /**
     * DELETE /api/items/{id}/cover
     * Rimuove copertina (solo owner).
     */
    @DELETE
    @Path("/{id}/cover")
    @RolesAllowed("user")
    public Response deleteCover(@PathParam("id") String bookId) {
        // TODO: rimuovere file da GridFS se non referenziato
        return Response.noContent().build();
    }
}