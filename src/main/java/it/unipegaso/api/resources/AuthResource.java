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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;


@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

	private static final Logger LOG = Logger.getLogger(AuthResource.class);

	@ConfigProperty(name = "quarkus.auth.otp.debug-mode", defaultValue = "false")
	boolean otpDebugMode;

	@ConfigProperty(name = "quarkus.session.duration-minutes", defaultValue = "120")
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

		if(!validRegistration(registrationData)) {
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

		// determina se il flag 'Secure' deve essere TRUE (solo se connessione HTTPS).
		boolean isSecure = uriInfo.getBaseUri().getScheme().equals("https");
		// setta il cookie per il flusso OTP
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
	public Response register(RegistrationDTO request) {

		// CONTROLLO DI SICUREZZA: tutti i valori devono essere presenti
		if (request.password() == null || request.password().isEmpty() || !request.acceptTerms() || !request.acceptPrivacy()) {

			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Password e accettazione termini sono obbligatori per la registrazione finale."))
					.build();
		}


		// HASHING DELLA PASSWORD
		String hashedPassword = BcryptUtil.bcryptHash(request.password());

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

				// genera ID di sessione a lunga durata 
				String authenticatedSessionId = UUID.randomUUID().toString(); 

				// salva lo stato autenticato in Redis (userId e username)
				registrationFlowService.saveAuthenticatedUser(authenticatedSessionId, newUser.id, newUser.username, sessionDurationMinutes * 60); 

				// determina se il flag 'Secure' deve essere TRUE
				boolean isSecure = uriInfo.getBaseUri().getScheme().equals("https");

				// crea e imposta il cookie SESSION_ID a LUNGO TERMINE 
				NewCookie authCookie = SessionIDProvider.createAuthenticatedSessionCookie(
						authenticatedSessionId, 
						isSecure, 
						sessionDurationMinutes * 60 // MaxAge in secondi
						); 

				Map<String, String> responseBody = Map.of(
						"message", "User created and authenticated.",
						"userId", newUser.id 
						);

				// ritorna la risposta con il nuovo Cookie SESSION_ID 
				return Response.status(Response.Status.CREATED)
						.entity(responseBody)
						.cookie(authCookie) 
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


	@POST
	@Path("/login")
	public Response login(LoginDTO credentials) {

		if (credentials.email() == null || credentials.email().isEmpty() ||
				credentials.password() == null || credentials.password().isEmpty()) {

			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ErrorResponse("REQUIRED_FIELDS_MISSING", "Identificatore (username/email) e password sono obbligatori."))
					.build();
		}

		LOG.debugf("Tentativo di login per: %s", credentials.email());

		// cerca l'utente 
		Optional<User> userOptional = userRepository.findByEmail(credentials.email());

		if (userOptional.isEmpty()) {
			// senza specificare se l'utente esiste o no 
			return Response.status(Response.Status.UNAUTHORIZED) 
					.entity(new ErrorResponse("INVALID_CREDENTIALS", "Credenziali non valide."))
					.build();
		}

		User user = userOptional.get();

		// verifica la password
		boolean isPasswordValid = BcryptUtil.matches(credentials.password(), user.hashedPassword);

		if (!isPasswordValid) {
			return Response.status(Response.Status.UNAUTHORIZED) 
					.entity(new ErrorResponse("INVALID_CREDENTIALS", "Credenziali non valide."))
					.build();
		}


		// session id a lunga durata
		String authenticatedSessionId = UUID.randomUUID().toString(); 

		int durationSeconds =  sessionDurationMinutes * 60;
		// salva lo stato autenticato in Redis
		registrationFlowService.saveAuthenticatedUser(authenticatedSessionId, user.id, user.username, durationSeconds); 

		// determina se il flag 'Secure' deve essere TRUE
		boolean isSecure = uriInfo.getBaseUri().getScheme().equals("https");

		// crea e imposta il cookie SESSION_ID a LUNGO TERMINE
		NewCookie authCookie = SessionIDProvider.createAuthenticatedSessionCookie(
				authenticatedSessionId, 
				isSecure, 
				durationSeconds
				); 

		// risposta di successo
		Map<String, String> responseBody = Map.of(
				"message", "Accesso effettuato con successo.",
				"userId", user.id 
				);

		return Response.ok(responseBody) // 200 OK
				.cookie(authCookie) // Imposta il SESSION_ID
				.build();
	}

	
	@POST
	@Path("/logout")
	public Response logout(@Context HttpHeaders headers) {
		LOG.info("Tentativo di logout utente.");

		String sessionId = SessionIDProvider.getSessionId(headers).orElse(null);

		if (sessionId != null) {
			// cancella lo stato di autenticazione da Redis (se l'utente era loggato)
			registrationFlowService.deleteSession(sessionId); 
		}

		// cancella il cookie nel browser (Max-Age=0)
		NewCookie expiredCookie = SessionIDProvider.createExpiredSessionCookie(sessionId); 

		return Response.noContent() 
				.cookie(expiredCookie)
				.build();
	}

}