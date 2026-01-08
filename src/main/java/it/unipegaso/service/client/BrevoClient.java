package it.unipegaso.service.client;

import it.unipegaso.api.dto.BrevoRequest;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "brevo-api")
public interface BrevoClient {
    @POST
    @Path("/v3/smtp/email")
    void sendEmail(
        @HeaderParam("api-key") String apiKey,
        BrevoRequest request
    ) throws Exception;
}
