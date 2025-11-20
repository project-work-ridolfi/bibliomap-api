package it.unipegaso.service;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;


@ApplicationScoped
public class RegistrationFlowService {

    @Inject
    SessionDataService sessionDataService;
    
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_USER_ID = "userId"; 
    private static final int OTP_SESSION_DURATION_SECONDS = 5 * 60; // 5 minuti per OTP    
    
    /**
     * @param sessionId L'ID della sessione.
     * @param email L'email fornita.
     * @param username L'username fornito.
     */
    public void saveInitialData(String sessionId, String email, String username) {
        Map<String, String> initialData = Map.of(
            FIELD_EMAIL, email,
            FIELD_USERNAME, username
        );
        
        // Salvataggio con scadenza breve per il flusso OTP
        sessionDataService.save(sessionId, initialData, OTP_SESSION_DURATION_SECONDS); 
    }
    

    /**
     * Recupera tutti i dati
     * @param sessionId L'ID della sessione.
     * @return Una mappa con tutti i dati (email, username, stato).
     */
    public Map<String, String> retrieveAllData(String sessionId) {
        return sessionDataService.getAll(sessionId);
    }

    /**
     * Elimina i dati della sessione di registrazione una volta completata.
     */
    public void deleteSession(String sessionId) {
        sessionDataService.delete(sessionId);
    }


    public void saveAuthenticatedUser(String sessionId, String userId, String username, int maxAgeSeconds) {
        Map<String, String> authData = Map.of(
            FIELD_USER_ID, userId,
            FIELD_USERNAME, username
        );
        
        // Salvataggio con scadenza lunga per l'autenticazione
        sessionDataService.save(sessionId, authData, maxAgeSeconds); 
    }
}