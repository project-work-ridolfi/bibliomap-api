package it.unipegaso.api.resources;

import static io.quarkus.elytron.security.common.BcryptUtil.bcryptHash;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.User;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

@QuarkusTest
@TestProfile(AuthResourceTestProfile.class) // mette le config con i mock a true
public class AuthResourceTest {

	@Inject
	UsersRepository usersRepository;


	private final String API_PATH = "/api/auth";
	private final String TEST_EMAIL = "flow_test_user@bibliomap.it";
	private final String TEST_USERNAME = "flowtestuser";
	private final String TEST_PASSWORD = "SecureP@ss123";

	// variabili per conservare lo stato tra i metodi di un singolo test
	private String sessionId;
	private String mockOtpCode;

	// dati di registrazione iniziali
	private final Map<String, String> registrationInitData = Map.of(
			"email", TEST_EMAIL,
			"username", TEST_USERNAME
			);

	// dati di login 
	private final Map<String, String> loginData = Map.of(
			"username", TEST_USERNAME,
			"password", TEST_PASSWORD
			);

	// dati di login con credenziali errate
	private final Map<String, String> wrongLoginData = Map.of(
			"username", TEST_USERNAME,
			"password", "WrongPassword"
			);

	// dati di registrazione finali 
	private final Map<String, Object> registrationFinalData = new HashMap<>() {{
		put("email", TEST_EMAIL);
		put("username", TEST_USERNAME);
		put("password", TEST_PASSWORD);
		put("acceptTerms", true);
		put("acceptPrivacy", true);
	}};

	
	private void cleanTestUsers() {
		usersRepository.delete(UsersRepository.EMAIL, TEST_EMAIL);
		usersRepository.delete(UsersRepository.EMAIL, "final_test@bibliomap.it");
	}

	@BeforeEach
	public void setup() {
		// esegue la pulizia prima di ogni test
		cleanTestUsers(); 
	}

	@AfterEach
	public void cleanup() {
		// esegue la pulizia dopo ogni test
		cleanTestUsers();
	}


	
	private void setupTestUser() {
		// pulizia preventiva immediata (se un test precedente ha fallito)
		usersRepository.delete(UsersRepository.EMAIL, TEST_EMAIL); 

		String hash = bcryptHash(TEST_PASSWORD);

		User user = new User();
		user.email = TEST_EMAIL;
		user.username = TEST_USERNAME;
		user.hashedPassword = hash; 
		user.acceptedTerms = true;
		user.id = "test-user-id"; // imposta un id fisso per i test
		usersRepository.create(user);
	}

	@Test
	public void testCompleteRegistrationFlow() {
		// /register-init

		Response responseInit = RestAssured.given()
				.contentType(MediaType.APPLICATION_JSON)
				.body(registrationInitData)
				.when().post(API_PATH + "/register-init");

		responseInit.then()
		.statusCode(200)
		.header("Set-Cookie", containsString("SESSION_ID"))
		.body("mockOtp", is(notNullValue()))
		.body("message", containsString("OTP inviato"));

		// estrae l'otp e il session id per i passi successivi
		mockOtpCode = responseInit.body().jsonPath().getString("mockOtp");
		sessionId = responseInit.getCookie("SESSION_ID");

		assertTrue(sessionId != null && sessionId.length() > 0, "la session id deve essere estratta.");
		assertTrue(mockOtpCode != null && mockOtpCode.length() > 0, "l'otp deve essere estratto.");

		// /register-verify (success)

		Map<String, String> verificationData = Map.of(
				"email", TEST_EMAIL,
				"otp", mockOtpCode
				);

		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.header("Cookie", "SESSION_ID=" + sessionId)
		.body(verificationData)
		.when().post(API_PATH + "/register-verify")
		.then()
		.statusCode(204); // 204 no content per successo


		// /register

		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.body(registrationFinalData)
		.when().post(API_PATH + "/register")
		.then()
		.statusCode(201)
		// verifica l'impostazione del cookie di autenticazione
		.cookie("access_token", is(notNullValue())) 
		.body("message", containsString("User created and authenticated"))
		.body("userId", is(notNullValue())); // verifica che l'id utente sia nel body

		Optional<User> createdUser = usersRepository.findByEmail(TEST_EMAIL);
		assertTrue(createdUser.isPresent(), "l'utente deve essere salvato nel db.");
	}

	@Test
	public void testRegisterInit_ExistingEmail_Conflict() {
		// prepara il db: inserisce l'utente prima del test
		User existingUser = new User();
		existingUser.email = TEST_EMAIL;
		existingUser.username = TEST_USERNAME;
		existingUser.hashedPassword = "somehash";
		existingUser.acceptedTerms = true;
		usersRepository.create(existingUser);

		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.body(registrationInitData)
		.when().post(API_PATH + "/register-init")
		.then()
		.statusCode(409)
		.body("error", equalTo("EMAIL_EXISTS"));
	}

	@Test
	public void testRegisterVerify_MissingSession_BadRequest() {
		// simula la mancanza del cookie (nessun header session_id)
		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.body(Collections.singletonMap("email", TEST_EMAIL))
		.when().post(API_PATH + "/register-verify")
		.then()
		.statusCode(400)
		.body("error", equalTo("MISSING_SESSION"));
	}

	@Test
	public void testRegisterVerify_InvalidOtp_FailsWithRetryDetails() {
		// setup: inizia il flusso per ottenere una session id valida e un otp valido
		Response responseInit = RestAssured.given()
				.contentType(MediaType.APPLICATION_JSON)
				.body(registrationInitData)
				.when().post(API_PATH + "/register-init");

		sessionId = responseInit.getCookie("SESSION_ID");

		Map<String, String> invalidVerificationData = Map.of(
				"email", TEST_EMAIL,
				"otp", "999999" // codice otp sbagliato
				);

		// primo tentativo fallito (dovrebbe ritornare 403 e retries: 2)
		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.header("Cookie", "SESSION_ID=" + sessionId)
		.body(invalidVerificationData)
		.when().post(API_PATH + "/register-verify")
		.then()
		.statusCode(403)
		.body("isBlocked", is(false))
		.body("retriesRemaining", equalTo(2));

		// secondo tentativo fallito (dovrebbe ritornare 403 e retries: 1)
		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.header("Cookie", "SESSION_ID=" + sessionId)
		.body(invalidVerificationData)
		.when().post(API_PATH + "/register-verify")
		.then()
		.statusCode(403)
		.body("retriesRemaining", equalTo(1));

		// terzo tentativo fallito (blocco: dovrebbe ritornare 403 e isblocked: true)
		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.header("Cookie", "SESSION_ID=" + sessionId)
		.body(invalidVerificationData)
		.when().post(API_PATH + "/register-verify")
		.then()
		.statusCode(403)
		.body("isBlocked", is(true))
		.body("retriesRemaining", equalTo(0));
	}


	@Test
	public void testLoginSuccess() {
		// setup: crea un utente nel db con hash dinamico
		setupTestUser();

		Response response = RestAssured.given()
				.contentType(MediaType.APPLICATION_JSON)
				.body(loginData)
				.when().post(API_PATH + "/login");

		response.then()
		.statusCode(200)
		// verifica che il corpo contenga l'id utente 
		.body("userId", equalTo("test-user-id"))
		// verifica la presenza del cookie 'access_token'
		.cookie("access_token", is(notNullValue()))
		// verifica che il cookie 'access_token' sia impostato con i flag di sicurezza
		.header("Set-Cookie", containsString("access_token"))
		.header("Set-Cookie", containsString("HttpOnly"));
	}

	@Test
	public void testLoginFailure_WrongPassword() {
		// setup: crea un utente nel db
		setupTestUser();

		Response response = RestAssured.given()
				.contentType(MediaType.APPLICATION_JSON)
				.body(wrongLoginData) // password sbagliata
				.when().post(API_PATH + "/login");

		response.then()
		.statusCode(401)
		.body("error", equalTo("INVALID_CREDENTIALS"))
		// verifica che l'header set-cookie non imposti il token
		.header("Set-Cookie", org.hamcrest.Matchers.not(containsString("access_token"))); 
	}

	@Test
	public void testLogoutSuccess() {
		// verifica che l'endpoint ritorni il cookie scaduto
		RestAssured.given()
		.contentType(MediaType.APPLICATION_JSON)
		.when().post(API_PATH + "/logout")
		.then()
		.statusCode(204) // 204 no content per cancellazione
		// verifica l'istruzione di cancellazione tramite max-age=0
		.header("Set-Cookie", containsString("access_token"))
		.header("Set-Cookie", containsString("Max-Age=0"));
	}
}