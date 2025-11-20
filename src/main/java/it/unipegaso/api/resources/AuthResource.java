package it.unipegaso.api.resources;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.elytron.security.common.BcryptUtil;
import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.LoginDTO;
import it.unipegaso.api.dto.RegistrationDTO;
import it.unipegaso.api.dto.VerificationDTO;
import it.unipegaso.api.util.SessionIDProvider;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.User;
import it.unipegaso.service.AuthTokenService;
import it.unipegaso.service.OtpService;
import it.unipegaso.service.RegistrationFlowService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Endpoint per autenticazione e gestione account.
 * Gestisce il flusso di registrazione tramite verifica OTP.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @ConfigProperty(name = "quarkus.auth.otp.debug-mode", defaultValue = "false")
    boolean otpDebugMode; 

    @Inject
    UsersRepository userRepository; 

    @Inject
    OtpService otpService; 

    @Inject
    RegistrationFlowService registrationFlowService;
    
    @Inject
    AuthTokenService authTokenService;

    /**
     * POST /api/auth/register-init
     * Inizia il processo di registrazione, genera OTP e invia email.
     * Ritorna: 200 OK + Cookie Sessione (+ OTP se debug=true) | 409 Conflict | 500 Internal Error
     */
    @POST
    @Path("/register-init")
    public Response registerInit(RegistrationDTO registrationData, @Context HttpHeaders headers,  @Context UriInfo uriInfo) {
    	
    	if(!validRegistration(registrationData)) {
    		return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Email e Username sono obbligatori per l'inizio della registrazione."))
                    .build();
    	}

        LOG.infof("Tentativo di inizio registrazione per email: %s", registrationData.email());

        // Recupera sid dal Cookie o ne genera uno nuovo se è la prima richiesta.
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(UUID.randomUUID().toString());
        
        // controlla non ci sia l'email nel db
        if (userRepository.findByEmail(registrationData.email()).isPresent()) {
            LOG.warnf("Tentativo di registrazione con email già esistente: %s", registrationData.email());

            return Response.status(Response.Status.CONFLICT) // 409 Conflict
                    .entity(new ErrorResponse("EMAIL_EXISTS", "Email già registrata."))
                    .build();
        }

        // Salva email e username in sessione
        registrationFlowService.saveInitialData(sessionId, registrationData.email(), registrationData.username());
       
        
        // Genera e invia OTP
        String mockOtp = otpService.generateAndSendOtp(registrationData.email(), sessionId, registrationData.username());

        
        // fallimento invio email
        if (mockOtp == null && !otpDebugMode) {
            LOG.errorf("Fallimento nell'invio email per %s. Controllare configurazione SMTP.", registrationData.email());
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR) // 500 Internal Error
                    .entity(new ErrorResponse("SMTP_FAILURE", "Impossibile inviare l'email di verifica. Riprovare o contattare l'assistenza."))
                    .build();
            
        }


        // risposta di successo (200)
        Map<String, String> responseData;
        
        if (otpDebugMode) {
            // debug include il mockOtp nel body
            responseData = Map.of(
                "message", "OTP inviato (Debug Mode).",
                "mockOtp", mockOtp
            );
        } else {
            responseData = Collections.singletonMap(
                "message", "Email di verifica inviata con successo."
            );
        }
        
        // Determina se il flag 'Secure' deve essere TRUE (solo se connessione HTTPS).
        boolean isSecure = uriInfo.getBaseUri().getScheme().equals("https");
        NewCookie sessionCookie = SessionIDProvider.createSessionCookie(sessionId, isSecure);

        
        return Response.ok(responseData) // 200 OK
                       .cookie(sessionCookie) 
                       .build();
    }

    private boolean validRegistration(RegistrationDTO request) {
    	boolean invalid = request.email() == null || request.email().isEmpty() || 
    	        request.username() == null || request.username().isEmpty();
    	
		return !invalid;
	}

	/**
     * POST /api/auth/register-verify
     * Verifica l'OTP fornito dall'utente.
     * Ritorna: 204 No Content (OK) | 403 Forbidden (Fallimento con dettagli retry)
     */
    @POST
    @Path("/register-verify")
    public Response registerVerify(VerificationDTO verificationData, @Context HttpHeaders headers) {
    	
        
        LOG.infof("Tentativo di verifica OTP per email: %s", verificationData.email());

        // recupera la sid dal Cookie
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

        if (sessionId == null) {
            LOG.errorf("Tentativo di verifica OTP fallito: Session ID mancante per %s.", verificationData.email());
            
            return Response.status(Response.Status.BAD_REQUEST) 
                    .entity(new ErrorResponse("MISSING_SESSION", "La sessione di verifica non è valida o è scaduta. Richiedi un nuovo codice."))
                    .build();
        }

        // tenta la verifica e ottiene lo stato completo (inclusi retry)
        Map<String, Object> result = otpService.verifyOtp(
            verificationData.email(), 
            verificationData.otp(), 
            sessionId
        );

        boolean isValid = (boolean) result.getOrDefault("valid", false);

        if (isValid) {
            // L'OTP è valido. L'utente può procedere.
            // NOTA: La logica di pulizia OTP/Retry è già nel service.
            
            return Response.noContent().build(); // 204 No Content
        } else {
            
            // Ritorniamo 403 Forbidden con il corpo JSON strutturato
            return Response.status(Response.Status.FORBIDDEN)
                           .entity(result) 
                           .build();
        }
    }


    @POST
    @Path("/register")
    public Response register(RegistrationDTO request) {
    	
    	// CONTROLLO DI SICUREZZA: tutti i valori devono essere presenti
    	if (request.password() == null || request.password().isEmpty() || !request.acceptTerms() || !request.acceptPrivacy()) {
            
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Password e accettazione termini sono obbligatori per la registrazione finale."))
                           .build();
        }
        

        // HASHING DELLA PASSWORD
        String hashedPassword = BcryptUtil.bcryptHash(request.password());

        // MAPPATURA (DTO -> Modello User)
        User newUser = new User();
        
        // Campi obbligatori e di sicurezza
        newUser.username = request.username();
        newUser.email = request.email();
        newUser.hashedPassword = hashedPassword;
        newUser.acceptedTerms = request.acceptTerms();
        Response response;

        try {
            // SALVATAGGIO UTENTE nel DB
        	
        	String id = userRepository.create(newUser);
            boolean success = id != null;

            if (success) {
            	// genera jwt
                String token = authTokenService.generateJwt(newUser); 
                
                //  parametri del Cookie HttpOnly
                int maxAgeSeconds = 900; // 15 minuti

                // crea il Cookie HTTP-Only e Secure
                NewCookie authCookie = new NewCookie.Builder("access_token") // Nome del cookie
                        .value(token)
                        .path("/") // Accessibile da tutta l'applicazione
                        .maxAge(maxAgeSeconds) 
                        .secure(false) //TODO mettere a true per https 
                        .httpOnly(true) // Impedisce l'accesso tramite JavaScript (Protezione XSS)
                        .sameSite(NewCookie.SameSite.LAX) 
                        .build();

                Map<String, String> responseBody = Map.of(
                    "message", "User created and authenticated.",
                    "userId", newUser.id 
                );

                // 4. Ritorna 201 Created con il corpo e il Cookie nell'header
                return Response.status(Response.Status.CREATED)
                               .entity(responseBody)
                               .cookie(authCookie) // <--- IMPOSTA IL COOKIE NELLA RISPOSTA
                               .build();
            } else {
                // fallimento logico non coperto
                LOG.errorf("Salvataggio utente fallito silenziosamente per %s. (Motivo non specificato).", request.email());
                 response = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                   .entity(new ErrorResponse("DB_FAILURE_SILENT", "Creazione utente fallita senza eccezione specifica."))
                                   .build();
            }
        
        } catch (IllegalArgumentException e) {
            // Cattura chiavi uniche duplicate (username/email)
            LOG.warnf("Tentativo di inserimento utente duplicato fallito: %s", e.getMessage());
            response = Response.status(Response.Status.CONFLICT)
                               .entity(new ErrorResponse("ALREADY_EXISTS", "L'username o l'email esistono già nel database."))
                               .build();
                               
        } catch (RuntimeException e) {
            // Cattura tutti gli altri errori di DB/runtime
            LOG.errorf(e, "Errore generico durante il salvataggio dell'utente %s.", request.email());
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity(new ErrorResponse("DB_SAVE_FAILURE", "Impossibile completare la registrazione a causa di un errore interno."))
                               .build();
        }
        
        return response;
    }

    /**
     * POST /api/auth/login
     * Autentica l'utente tramite credenziali.
     * Ritorna: 200 OK + Cookie access_token | 401 Unauthorized
     */
    @POST
    @Path("/login")
    public Response login(LoginDTO credentials) {
        
        if (credentials.username() == null || credentials.username().isEmpty() ||
            credentials.password() == null || credentials.password().isEmpty()) {
            
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Identificatore (username/email) e password sono obbligatori."))
                           .build();
        }
        
        LOG.infof("Tentativo di login per: %s", credentials.username());

        // cerca l'utente per id
        Optional<User> userOptional = userRepository.findByUsername(credentials.username());
        
        if (userOptional.isEmpty()) {
            // se non lo trova
            return Response.status(Response.Status.UNAUTHORIZED) // 401 Unauthorized
                           .entity(new ErrorResponse("INVALID_CREDENTIALS", "Credenziali non valide."))
                           .build();
        }
        
        User user = userOptional.get();

        // verifica la password
        boolean isPasswordValid = BcryptUtil.matches(credentials.password(), user.hashedPassword);

        if (!isPasswordValid) {
            return Response.status(Response.Status.UNAUTHORIZED) // 401 Unauthorized
                           .entity(new ErrorResponse("INVALID_CREDENTIALS", "Credenziali non valide."))
                           .build();
        }

        // se l'utente è valido: genera JWT e imposta il Cookie HttpOnly
        String token = authTokenService.generateJwt(user); 
        
        int maxAgeSeconds = 900; // Access Token: 15 minuti

        NewCookie authCookie = new NewCookie.Builder("access_token") 
                .value(token)
                .path("/")
                .maxAge(maxAgeSeconds)
                .secure(true) // Assumi HTTPS
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();

        // risposta di successo
        Map<String, String> responseBody = Map.of(
            "message", "Accesso effettuato con successo.",
            "userId", user.id 
        );

        return Response.ok(responseBody) // 200 OK
                       .cookie(authCookie)
                       .build();
    }
    
    /**
     * POST /api/auth/logout
     * Cancella il cookie di autenticazione 'access_token' forzando il logout.
     * Ritorna: 204 No Content.
     */
    @POST
    @Path("/logout")
    public Response logout() {
        LOG.info("Tentativo di logout utente.");

        // crea un nuovo cookie con lo stesso nome, ma con Max-Age=0
        NewCookie expiredCookie = new NewCookie.Builder("access_token")
                .value("")
                .path("/")
                .maxAge(0) // Imposta la scadenza immediata (cancellazione)
                .secure(true) 
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
        
        return Response.noContent() // 204 No Content
                       .cookie(expiredCookie)
                       .build();
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
