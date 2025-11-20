package it.unipegaso.service;

import java.time.LocalDateTime;

import it.unipegaso.api.dto.LibraryDTO;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LibraryService {

    @Inject
    LibrariesRepository librariesRepository;
    
    @Inject
    UsersRepository usersRepository; // per recuperare ID utente
    
    @Inject
    SessionDataService sessionDataService; // per recuperare username

    /**
     * Crea una nuova libreria e la collega all'utente e alla sua posizione.
     * @param sessionId id sessione
     * @param libraryDTO dati libreria
     * @return id libreria creata
     */
    public String createNewLibrary(String sessionId, LibraryDTO libraryDTO) {
        
        // RECUPERO UTENTE 
        String username = sessionDataService.get(sessionId, "username").orElseThrow(() -> new IllegalStateException("username mancante sessione"));
        
        User user = usersRepository.findByUsername(username).orElseThrow(() -> new IllegalStateException("utente non trovato db"));
        
        Library newLibrary = new Library();
        newLibrary.name = libraryDTO.name();
        newLibrary.ownerId = user.id;
        newLibrary.visibility = libraryDTO.visibility();
        
        // collegamento della posizione del profilo
        if ("user_default".equals(libraryDTO.locationType()) && user.locationId != null) {
            newLibrary.locationId = user.locationId;
        } 
        // TODO: Gestire "new_location" navigando a un altro endpoint di creazione posizione.
        
        LocalDateTime now = LocalDateTime.now();
        newLibrary.createdAt = now;
        newLibrary.modifiedAt = now;

        try {
            return librariesRepository.create(newLibrary);
        } catch (Exception e) {
            throw new RuntimeException("fallimento creazione libreria db", e);
        }
    }
}