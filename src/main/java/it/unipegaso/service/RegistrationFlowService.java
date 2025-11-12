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

    //TODO da capire se servono altre chiavi
    
    
    /**
     * @param sessionId L'ID della sessione.
     * @param email L'email fornita.
     * @param username L'username fornito.
     */
    public void saveInitialData(String sessionId, String email, String username) {
        
        // Creiamo la mappa dei dati da salvare nell'Hash di Redis
        Map<String, String> initialData = Map.of(
            FIELD_EMAIL, email,
            FIELD_USERNAME, username
        );
        
        sessionDataService.save(sessionId, initialData);
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
}