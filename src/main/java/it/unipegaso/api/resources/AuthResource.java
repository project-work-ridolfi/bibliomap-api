package it.unipegaso.api.resources;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import it.unipegaso.api.dto.RegistrationInitDTO;
import it.unipegaso.database.UserRepository;
import it.unipegaso.service.OtpService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per autenticazione e gestione account.
 * 
 * Implementa:
 * - Registrazione con consensi GDPR
 * - Login con JWT
 * - Refresh token
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
	
	private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    UserRepository userRepository; // Per il controllo email

    @Inject
    OtpService otpService; // Per l'invio OTP
    
    // Iniettiamo qui il mock flag dal servizio per decidere cosa mostrare
    @Inject
    @ConfigProperty(name = "quarkus.auth.otp.mock-enabled", defaultValue = "true")
    boolean mockOtpEnabled; 
    

    /**
     * POST /api/auth/register-init
     * Inizia il processo di registrazione: controlla l'email, genera OTP e invia email.
     * * Ritorna:
     * - 202 Accepted + (OTP se mock=true)
     * - 409 Conflict se l'email esiste
     * - 500 Internal Server Error in caso di errore di invio email
     */
    @POST
    @Path("/register-init")
    public Response registerInit(RegistrationInitDTO registrationData) {
        
        LOG.infof("Tentativo di inizio registrazione per email: %s", registrationData.email());

        // 1. Controllo email nel DB
        if (userRepository.findByEmail(registrationData.email()).isPresent()) {
            LOG.warnf("Tentativo di registrazione con email già esistente: %s", registrationData.email());
            
            // Ritorna 409 Conflict se l'email esiste già
            return Response.status(Response.Status.CONFLICT) 
                           .entity("{\"error\": \"EMAIL_EXISTS\", \"message\": \"Email già registrata.\"}")
                           .build();
        }

        
        // Invio Email (usa mock o mailer reale)
        String otpCode = otpService.generateRandomOtpCode(6); //TODO a conf
        boolean emailSent = otpService.sendOtp(registrationData.email(), otpCode);
        
        if (!emailSent) {
            // Gestione Errore SMTP
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"SMTP_FAILURE\", \"message\": \"Impossibile inviare l'email di verifica.\"}")
                           .build();
        }
        
        // 4. Successo: Restituisce l'OTP solo se mock-enabled è TRUE
        
        // Costruiamo la risposta JSON. Usiamo un oggetto DTO per la risposta per maggiore chiarezza.
        // Qui usiamo una stringa per semplicità nel contesto della chat.
        String otpValue = mockOtpEnabled ? otpCode : null;
        
        String responseBody = String.format(
            "{\"message\": \"OTP inviato.\", \"mockOtp\": \"%s\"}", 
            otpValue
        );
        
        return Response.status(Response.Status.ACCEPTED) // 202 Accepted
                       .entity(responseBody)
                       .build();
    }

    /**
     * POST /api/auth/register
     * Body: { "username": "...", "email": "...", "password": "...", "acceptTerms": true }
     */
    @POST
    @Path("/register")
    public Response register(Object registrationDto) {
        // TODO: implementare registrazione + consensi
        return Response.status(Response.Status.CREATED)
                .entity("{\"message\": \"User created (TODO)\"}")
                .build();
    }

    /**
     * POST /api/auth/login
     * Body: { "username": "...", "password": "..." }
     * Response: { "token": "eyJ...", "refreshToken": "..." }
     */
    @POST
    @Path("/login")
    public Response login(Object loginDto) {
        // TODO: validare credenziali + generare JWT
        return Response.ok("{\"token\": \"fake-jwt-token\"}").build();
    }

    /**
     * POST /api/auth/refresh
     * Headers: Authorization: Bearer <refresh-token>
     * Response: { "token": "new-jwt" }
     */
    @POST
    @Path("/refresh")
    public Response refreshToken() {
        // TODO: validare refresh token + generare nuovo JWT
        return Response.ok("{\"token\": \"new-fake-jwt\"}").build();
    }
}
