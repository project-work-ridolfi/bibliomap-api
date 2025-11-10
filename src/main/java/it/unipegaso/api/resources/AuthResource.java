package it.unipegaso.api.resources;

import java.time.LocalDateTime;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.elytron.security.common.BcryptUtil;
import it.unipegaso.api.dto.ErrorResponse;
import it.unipegaso.api.dto.MessageResponse;
import it.unipegaso.api.dto.RegistrationFinalDTO;
import it.unipegaso.api.dto.RegistrationInitDTO;
import it.unipegaso.api.dto.VerificationDTO;
import it.unipegaso.database.UserRepository;
import it.unipegaso.database.model.User;
import it.unipegaso.service.OtpService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint per autenticazione e gestione account.
 * Gestisce il flusso di registrazione tramite verifica OTP.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);
    private static final String SESSION_ID_HEADER = "X-Session-Id";
    private static final String DEFAULT_SESSION_ID = "NO_SESSION";

    @Inject
    UserRepository userRepository; 

    @Inject
    OtpService otpService; 
    
    @ConfigProperty(name = "quarkus.auth.otp.debug-mode", defaultValue = "false")
    boolean otpDebugMode; 


    /**
     * POST /api/auth/register-init
     * Inizia il processo di registrazione, genera OTP e invia email.
     * Ritorna: 202 Accepted + (OTP se debug=true) | 409 Conflict | 500 Internal Error
     */
    @POST
    @Path("/register-init")
    public Response registerInit(RegistrationInitDTO registrationData, @Context HttpHeaders headers) {
        
        LOG.infof("Tentativo di inizio registrazione per email: %s", registrationData.email());

        // Controllo email nel DB
        if (userRepository.findByEmail(registrationData.email()).isPresent()) {
            LOG.warnf("Tentativo di registrazione con email già esistente: %s", registrationData.email());
            
            // 409 se l'email esiste già
            return Response.status(Response.Status.CONFLICT) 
                           .entity("{\"error\": \"EMAIL_EXISTS\", \"message\": \"Email già registrata.\"}")
                           .build();
        }

        // Recupera l'ID Sessione per legare l'OTP (Chiave di sicurezza HOTP)
        String sessionId = Optional.ofNullable(headers.getRequestHeader(SESSION_ID_HEADER))
                                   .flatMap(list -> list.stream().findFirst())
                                   .orElse(DEFAULT_SESSION_ID);
                           
        // Genera e Salva OTP in Redis, poi invia l'email.
        // mockOtp contiene il codice solo se otpDebugMode è TRUE.
        String mockOtp = otpService.generateAndSendOtp(registrationData.email(), sessionId);
        
        // Gestione risposta
        if (mockOtp == null && !otpDebugMode) {
            // Se non siamo in debug, null significa che c'è stato un fallimento (es. SMTP_FAILURE)
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"SMTP_FAILURE\", \"message\": \"Impossibile inviare l'email di verifica.\"}")
                           .build();
        }
        
        // Successo: Restituisce l'OTP solo in modalità debug/mock
        String otpValue = otpDebugMode ? mockOtp : "OTP inviato (solo visibile in debug mode)";
        
        String responseBody = String.format(
            "{\"message\": \"OTP inviato.\", \"mockOtp\": \"%s\"}", 
            otpValue
        );
        
        return Response.status(Response.Status.ACCEPTED) // 202 Accepted
                       .entity(responseBody)
                       .build();
    }


    /**
     * POST /api/auth/register-verify
     * Fase 2: Verifica l'OTP fornito dall'utente.
     * Ritorna: 200 OK | 401 Unauthorized
     */
    @POST
    @Path("/register-verify")
    public Response registerVerify(VerificationDTO verificationData) {
        
        LOG.infof("Tentativo di verifica OTP per email: %s", verificationData.email());

        // Verifica l'OTP in Redis (controlla validità/scadenza e garantisce uso singolo)
        if (otpService.verifyOtp(verificationData.email(), verificationData.otp())) {
            
            // L'OTP è valido. L'utente può procedere.
            return Response.noContent().build();
        } else {
            // L'OTP non è valido, scaduto o già usato (o max retry raggiunto nella logica futura)
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("{\"error\": \"INVALID_OTP\", \"message\": \"Codice OTP non valido o scaduto.\"}")
                           .build();
        }
    }


    @POST
    @Path("/register")
    public Response register(RegistrationFinalDTO registrationDto) {
        
        // CONTROLLO DI SICUREZZA: Accettazione Termini
        if (!registrationDto.acceptTerms()) {
            LOG.errorf("Tentativo di registrazione senza accettare i termini da: %s", registrationDto.email());
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("TERMS_REQUIRED", "Devi accettare i termini e condizioni per proseguire."))
                           .build();
        }

        // HASHING DELLA PASSWORD
        String hashedPassword = BcryptUtil.bcryptHash(registrationDto.password());

        // MAPPATURA (DTO -> Modello User)
        User newUser = new User();
        
        // Campi obbligatori e di sicurezza
        newUser.username = registrationDto.username();
        newUser.email = registrationDto.email();
        newUser.hashedPassword = hashedPassword;
        newUser.acceptedTerms = registrationDto.acceptTerms();
        newUser.createdAt = LocalDateTime.now(); 
        Response response;

        try {
            // SALVATAGGIO UTENTE nel DB
            boolean success = userRepository.createUser(newUser);

            if (success) {
                // L'inserimento ha avuto successo, risponde 201 Created.
                response = Response.status(Response.Status.CREATED)
                                   .entity(new MessageResponse("User created successfully."))
                                   .build();
            } else {
                // fallimento logico non coperto
                LOG.errorf("Salvataggio utente fallito silenziosamente per %s. (Motivo non specificato).", registrationDto.email());
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
            LOG.errorf(e, "Errore generico durante il salvataggio dell'utente %s.", registrationDto.email());
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity(new ErrorResponse("DB_SAVE_FAILURE", "Impossibile completare la registrazione a causa di un errore interno."))
                               .build();
        }
        
        return response;
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