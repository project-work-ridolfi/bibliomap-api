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
import it.unipegaso.service.OtpService;
import it.unipegaso.service.RegistrationFlowService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @ConfigProperty(name = "email.debug-mode", defaultValue = "false")
    boolean otpDebugMode;

    @ConfigProperty(name = "session.duration-minutes", defaultValue = "120")
    int sessionDurationMinutes;

    @Inject
    UsersRepository userRepository;

    @Inject
    OtpService otpService;

    @Inject
    RegistrationFlowService registrationFlowService;

    @Context
    UriInfo uriInfo;

    @POST
    @Path("/register-init")
    public Response registerInit(RegistrationDTO registrationData, @Context HttpHeaders headers) {

        if (!validRegistration(registrationData)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Email e Username sono obbligatori per l'inizio della registrazione."))
                    .build();
        }

        LOG.infof("Tentativo di inizio registrazione per email: %s", registrationData.email());

        // recupera sid dal Cookie o ne genera uno nuovo se e' la prima richiesta.
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(UUID.randomUUID().toString());

        // controlla non ci sia l'email nel db
        if (userRepository.findByEmail(registrationData.email()).isPresent()) {
            LOG.warnf("Tentativo di registrazione con email già esistente: %s", registrationData.email());

            return Response.status(Response.Status.CONFLICT) // 409 Conflict
                    .entity(new ErrorResponse("EMAIL_EXISTS", "Email già registrata."))
                    .build();
        }

        // salva email e username in sessione
        registrationFlowService.saveInitialData(sessionId, registrationData.email(), registrationData.username());

        // genera e invia OTP
        String mockOtp = otpService.generateAndSendOtp(registrationData.email(), sessionId, registrationData.username(), false);

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
                    "mockOtp", mockOtp);
        } else {
            responseData = Collections.singletonMap(
                    "message", "Email di verifica inviata con successo.");
        }

        // determina se il flag 'Secure' deve essere TRUE (solo se connessione HTTPS).
        boolean isSecure = isRequestSecure(uriInfo, headers);

        // GENERA HEADER STRINGA
        // Durata breve per la fase di registrazione (es. 1 ora)
        int otpDuration = 3600; 
        String cookieHeader = SessionIDProvider.buildSetCookieHeader(
                sessionId,
                otpDuration,
                isSecure);

        return Response.ok(responseData)
                .header("Set-Cookie", cookieHeader)
                .build();
    }

    private boolean validRegistration(RegistrationDTO request) {
        boolean invalid = request.email() == null || request.email().isEmpty() ||
                request.username() == null || request.username().isEmpty();

        return !invalid;
    }

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
                sessionId);

        boolean isValid = (boolean) result.getOrDefault("valid", false);

        if (isValid) {
            // L'OTP e' valido. L'utente puo' procedere.
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
    public Response register(RegistrationDTO request, @Context HttpHeaders headers) {

        // CONTROLLO DI SICUREZZA: tutti i valori devono essere presenti
        if (request.password() == null || request.password().isEmpty() || !request.acceptTerms() || !request.acceptPrivacy()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Password e accettazione termini sono obbligatori per la registrazione finale."))
                    .build();
        }

        // HASHING DELLA PASSWORD
        String hashedPassword = BcryptUtil.bcryptHash(request.password());

        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setHashedPassword(hashedPassword);
        newUser.setAcceptedTerms(request.acceptTerms());

        try {
            // SALVATAGGIO UTENTE nel DB
            String id = userRepository.create(newUser);
            boolean success = id != null;

            if (success) {
                String authenticatedSessionId = UUID.randomUUID().toString();
                int durationSeconds = sessionDurationMinutes * 60;
                registrationFlowService.saveAuthenticatedUser(
                        authenticatedSessionId,
                        newUser.getId(),
                        newUser.getUsername(),
                        durationSeconds);

                boolean isSecure = isRequestSecure(uriInfo, headers);

                String cookieHeader = SessionIDProvider.buildSetCookieHeader(authenticatedSessionId, durationSeconds, isSecure);

                Map<String, String> responseBody = Map.of(
                        "message", "User created and authenticated.",
                        "userId", newUser.getId());

                return Response.status(Response.Status.CREATED)
                        .header("Set-Cookie", cookieHeader)
                        .entity(responseBody)
                        .build();

            } else {
                LOG.errorf("Salvataggio utente fallito silenziosamente per %s. (Motivo non specificato).", request.email());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse("DB_FAILURE_SILENT", "Creazione utente fallita senza eccezione specifica."))
                        .build();
            }

        } catch (IllegalArgumentException e) {
            LOG.warnf("Tentativo di inserimento utente duplicato fallito: %s", e.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse("ALREADY_EXISTS", "L'username o l'email esistono già nel database."))
                    .build();

        } catch (RuntimeException e) {
            LOG.errorf(e, "Errore generico durante il salvataggio dell'utente %s.", request.email());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("DB_SAVE_FAILURE", "Impossibile completare la registrazione a causa di un errore interno."))
                    .build();
        }
    }

    @POST
    @Path("/login")
    public Response login(LoginDTO credentials, @Context HttpHeaders headers) {

        if (credentials.email() == null || credentials.email().isEmpty() ||
                credentials.password() == null || credentials.password().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Identificatore (username/email) e password sono obbligatori."))
                    .build();
        }

        LOG.debugf("Tentativo di login per: %s", credentials.email());

        Optional<User> userOptional = userRepository.findByEmail(credentials.email());
        if (userOptional.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("INVALID_CREDENTIALS", "Credenziali non valide."))
                    .build();
        }

        User user = userOptional.get();
        boolean isPasswordValid = BcryptUtil.matches(credentials.password(), user.getHashedPassword());

        if (!isPasswordValid) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("INVALID_CREDENTIALS", "Credenziali non valide."))
                    .build();
        }

        // session id a lunga durata
        String authenticatedSessionId = UUID.randomUUID().toString();
        int durationSeconds = sessionDurationMinutes * 60;

        registrationFlowService.saveAuthenticatedUser(authenticatedSessionId, user.getId(), user.getUsername(), durationSeconds);

        boolean isSecure = uriInfo.getBaseUri().getScheme().equals("https");

        Map<String, String> responseBody = Map.of(
                "message", "Accesso effettuato con successo.",
                "userId", user.getId());

        String cookieHeader = SessionIDProvider.buildSetCookieHeader(
                authenticatedSessionId,
                durationSeconds,
                isSecure);

        return Response.ok(responseBody)
                .header("Set-Cookie", cookieHeader)
                .build();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context HttpHeaders headers) {
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);
        LOG.infof("Tentativo di logout per sessione: %s", sessionId);

        if (sessionId != null) {
            registrationFlowService.deleteSession(sessionId);
        }

        boolean isSecure =isRequestSecure(uriInfo, headers);
        String expiredCookieHeader = SessionIDProvider.buildExpiredSetCookieHeader(isSecure);

        return Response.noContent()
                .header("Set-Cookie", expiredCookieHeader)
                .build();
    }

    @POST
    @Path("/password-reset-init")
    public Response passwordResetInit(Map<String, String> request, @Context HttpHeaders headers) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("EMAIL_REQUIRED", "L'email è obbligatoria per il recupero password."))
                    .build();
        }

        // Verifica se l'utente esiste
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            LOG.warnf("Richiesta recupero password per email inesistente: %s", email);
            return Response.ok(Map.of("message", "Se l'email è registrata, riceverai un codice OTP.")).build();
        }

        User user = userOpt.get();

        // recupera sid dal Cookie o ne genera uno nuovo
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(UUID.randomUUID().toString());

        // genera e invia OTP con OtpService
        String mockOtp = otpService.generateAndSendOtp(email, sessionId, user.getUsername(), true);

        if (mockOtp == null && !otpDebugMode) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("SMTP_FAILURE", "Impossibile inviare l'email di recupero."))
                    .build();
        }

        // Risposta
        Map<String, String> responseData = otpDebugMode
                ? Map.of("message", "OTP inviato (Debug).", "mockOtp", mockOtp)
                : Map.of("message", "Codice di verifica inviato.");

        boolean isSecure = isRequestSecure(uriInfo, headers);
        int otpDuration = 3600; // Durata temporanea per OTP

        String cookieHeader = SessionIDProvider.buildSetCookieHeader(sessionId, otpDuration, isSecure);

        return Response.ok(responseData)
                .header("Set-Cookie", cookieHeader)
                .build();
    }

    @POST
    @Path("/password-reset-verify")
    public Response passwordResetVerify(Map<String, String> request, @Context HttpHeaders headers) {
        String email = request.get("email");
        String otp = request.get("otp");
        String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

        if (sessionId == null || email == null || otp == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("MISSING_DATA", "Dati mancanti o sessione scaduta."))
                    .build();
        }

        // Riutilizzo della logica di verifica esistente
        Map<String, Object> result = otpService.verifyOtp(email, otp, sessionId);
        boolean isValid = (boolean) result.getOrDefault("valid", false);

        if (isValid) {
            return Response.noContent().build(); // 204
        } else {
            return Response.status(Response.Status.FORBIDDEN).entity(result).build();
        }
    }

    @PUT
    @Path("/password-reset-complete")
    public Response passwordResetComplete(Map<String, Object> request) {
        String email = (String) request.get("email");
        String newPassword = (String) request.get("new");
        Boolean fromOtp = (Boolean) request.get("fromOtp");

        if (email == null || newPassword == null || fromOtp == null || !fromOtp) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("INVALID_REQUEST", "Richiesta non valida.")).build();
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        User user = userOpt.get();

        // Hashing della nuova password
        String hashedPassword = BcryptUtil.bcryptHash(newPassword);
        user.setHashedPassword(hashedPassword);

        // Aggiunta alla history
        Map<String, Object> historyEntry = Map.of(
                "action", "PASSWORD_RESET_VIA_OTP",
                "changedOn", new java.util.Date());
        user.addToHistory(historyEntry);

        // Salvataggio nel DB
        userRepository.update(user);

        return Response.ok(Map.of("message", "Password aggiornata con successo.")).build();
    }

    // per capire se siamo davvero in HTTPS 
    private boolean isRequestSecure(UriInfo uriInfo, HttpHeaders headers) {
        // controlla se il server vede HTTPS direttamente
        if (uriInfo.getBaseUri().getScheme().equals("https")) {
            return true;
        }
        // controlla l'header standard dei proxy 
        String forwardedProto = headers.getHeaderString("X-Forwarded-Proto");
        if (forwardedProto != null && forwardedProto.equalsIgnoreCase("https")) {
            return true;
        }
        return false;
    }
}
