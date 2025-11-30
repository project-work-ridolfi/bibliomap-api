package it.unipegaso.api.resources;

import java.io.IOException;

import org.jboss.logging.Logger;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOG = Logger.getLogger(LoggingFilter.class);
    
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        final String method = requestContext.getMethod();
        final String path = requestContext.getUriInfo().getPath();
        
        LOG.infof(">>> INCOMING REQUEST: %s %s", method, path);
        
        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader != null) {
            LOG.debugf("Auth: %s", authHeader.substring(0, Math.min(authHeader.length(), 20)) + "...");
        }
    }
    
    @Override
    public void filter(ContainerRequestContext requestContext, 
                       ContainerResponseContext responseContext) throws IOException {
        
        final String method = requestContext.getMethod();
        final String path = requestContext.getUriInfo().getPath();
        final int status = responseContext.getStatus();
        
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "http://localhost:5173"); //TODO
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().add("Access-Control-Allow-Headers", 
            "Content-Type, Authorization, X-Session-Id, Accept");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", 
            "GET, POST, PUT, DELETE, OPTIONS");
        responseContext.getHeaders().add("Access-Control-Max-Age", "3600");
        
        LOG.infof("<<< OUTGOING RESPONSE: %s %s -> Status %d", method, path, status);
        
        Object entity = responseContext.getEntity();
        if (entity != null) {
            LOG.debugf("Response Body: %s", entity.toString());
        }
    }
}