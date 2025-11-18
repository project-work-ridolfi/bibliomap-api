package it.unipegaso.api.resources;

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
@TestProfile(AuthResourceTestProfile.class) //mette le config con i mock a true
public class AuthResourceTest {
    
    @Inject
    UsersRepository usersRepository;
    

    private final String API_PATH = "/api/auth";
    private final String TEST_EMAIL = "flow_test_user@bibliomap.it";
    private final String TEST_USERNAME = "flowtestuser";
    private final String TEST_PASSWORD = "SecureP@ss123";

    // Variabili per conservare lo stato tra i metodi di un singolo test
    private String sessionId;
    private String mockOtpCode;

    // Dati di registrazione iniziali
    private final Map<String, String> registrationInitData = Map.of(
        "email", TEST_EMAIL,
        "username", TEST_USERNAME
    );

    // Dati di registrazione finali (simulati)
    private final Map<String, Object> registrationFinalData = new HashMap<>() {{
        put("email", TEST_EMAIL);
        put("username", TEST_USERNAME);
        put("password", TEST_PASSWORD);
        put("acceptTerms", true);
        put("acceptPrivacy", true);
    }};

    @AfterEach
    public void cleanup() {
        // Pulizia atomica: usa la chiave email per la reversibilità
        usersRepository.delete(UsersRepository.EMAIL, TEST_EMAIL);
        // Pulizia utente creato nel test finale semplificato (se l'email è diversa)
        usersRepository.delete(UsersRepository.EMAIL, "final_test@bibliomap.it");
    }
    
    @Test
    public void testCompleteRegistrationFlow() {
        //  /register-init
        
        Response responseInit = RestAssured.given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(registrationInitData)
            .when().post(API_PATH + "/register-init");
            
        responseInit.then()
            .statusCode(200) 
            .header("Set-Cookie", containsString("SESSION_ID"))
            .body("mockOtp", is(notNullValue())) // Risolto l'errore di sintassi
            .body("message", containsString("OTP inviato"));
            
        // Estrae l'OTP e il Session ID per i passi successivi
        mockOtpCode = responseInit.body().jsonPath().getString("mockOtp");
        sessionId = responseInit.getCookie("SESSION_ID");
        
        assertTrue(sessionId != null && sessionId.length() > 0, "La Session ID deve essere estratta.");
        assertTrue(mockOtpCode != null && mockOtpCode.length() > 0, "L'OTP deve essere estratto.");

        // /register-verify (SUCCESS)
        
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
            .statusCode(204); // 204 No Content per successo


        //  /register
        
        RestAssured.given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(registrationFinalData)
            .when().post(API_PATH + "/register")
            .then()
            .statusCode(201) 
            .body("message", containsString("User created successfully"));
            
        Optional<User> createdUser = usersRepository.findByEmail(TEST_EMAIL);
        assertTrue(createdUser.isPresent(), "L'utente deve essere salvato nel DB.");
    }

    @Test
    public void testRegisterInit_ExistingEmail_Conflict() {
        // Prepara il DB: Inserisce l'utente prima del test
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
        // Simula la mancanza del Cookie (nessun header SESSION_ID)
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
        // Setup: Inizia il flusso per ottenere una Session ID valida e un OTP valido
        Response responseInit = RestAssured.given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(registrationInitData)
            .when().post(API_PATH + "/register-init");
        
        sessionId = responseInit.getCookie("SESSION_ID");
        
        Map<String, String> invalidVerificationData = Map.of(
            "email", TEST_EMAIL,
            "otp", "999999" // Codice OTP sbagliato
        );

        // Primo tentativo fallito (Dovrebbe ritornare 403 e retries: 2)
        RestAssured.given()
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", "SESSION_ID=" + sessionId)
            .body(invalidVerificationData)
            .when().post(API_PATH + "/register-verify")
            .then()
            .statusCode(403) 
            .body("isBlocked", is(false))
            .body("retriesRemaining", equalTo(2)); 

        // Secondo tentativo fallito (Dovrebbe ritornare 403 e retries: 1)
        RestAssured.given()
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", "SESSION_ID=" + sessionId)
            .body(invalidVerificationData)
            .when().post(API_PATH + "/register-verify")
            .then()
            .statusCode(403) 
            .body("retriesRemaining", equalTo(1));

        // Terzo tentativo fallito (BLOCCO: Dovrebbe ritornare 403 e isBlocked: true)
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
    
    
}



