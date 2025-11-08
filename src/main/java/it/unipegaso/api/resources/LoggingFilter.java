package it.unipegaso.api.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.jboss.logging.Logger;

/**
 * Filtro generico per loggare tutte le richieste HTTP e le relative risposte.
 * L'annotazione @Provider assicura che Quarkus lo registri automaticamente.
 */
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(LoggingFilter.class);

    // LOG DELLA RICHIESTA
    
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        final String method = requestContext.getMethod();
        final String path = requestContext.getUriInfo().getPath();
        
        // Logga il metodo HTTP, il percorso e l'IP 
        LOG.infof(">>> INCOMING REQUEST: %s %s", method, path);
        
        // Logga anche gli header utili
        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader != null) {
            LOG.debugf("Auth: %s", authHeader.substring(0, Math.min(authHeader.length(), 20)) + "...");
        }
    }

    // LOG DELLA RISPOSTA 
    
    @Override
    public void filter(ContainerRequestContext requestContext, 
                       ContainerResponseContext responseContext) throws IOException {
        
        final String method = requestContext.getMethod();
        final String path = requestContext.getUriInfo().getPath();
        final int status = responseContext.getStatus();
        
        // Logga il metodo HTTP, il percorso e lo stato di risposta
        LOG.infof("<<< OUTGOING RESPONSE: %s %s -> Status %d", method, path, status);
        
        // logga il corpo della risposta (solo in debug, può contenere dati sensibili)
         Object entity = responseContext.getEntity();
         if (entity != null) {
             LOG.debugf("Response Body: %s", entity.toString());
         }
    }
}
