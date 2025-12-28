package it.unipegaso.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import com.mongodb.client.FindIterable;

import it.unipegaso.api.dto.LibraryDTO;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.User;
import it.unipegaso.database.model.VisibilityOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LibraryService {

	private static final Logger LOG = Logger.getLogger(LibraryService.class);


	@Inject
	LibrariesRepository librariesRepository;

	@Inject
	UsersRepository usersRepository; // per recuperare ID utente

	@Inject
	SessionDataService sessionDataService; // per recuperare username

	/**
	 * Crea una nuova libreria e la collega all'utente e alla sua posizione.
	 * @return id libreria creata
	 */
	public String createNewLibrary(User user, LibraryDTO libraryDTO) {

		LOG.debug("create new library init");
		LOG.debug("username: " + user.getUsername());

		Library newLibrary = new Library();
		newLibrary.setName(libraryDTO.name());
		newLibrary.setOwnerId(user.getId());
		
		//controllo che visibility sia corretta
		String visibilityInput = libraryDTO.visibility();

		VisibilityOptions visibility = VisibilityOptions.fromString(visibilityInput)
		            .orElseThrow();

		String normalizedVisibility = visibility.toDbValue();
		newLibrary.setVisibility(normalizedVisibility);

		LOG.info("library creata");

		// collegamento della posizione del profilo
		if ("user_default".equals(libraryDTO.locationType()) && user.getLocationId() != null) {

			LOG.debug("user default");
			newLibrary.setLocationId(user.getLocationId());
		} 
		// TODO: Gestire "new_location" navigando a un altro endpoint di creazione posizione.

		LocalDateTime now = LocalDateTime.now();
		newLibrary.setCreatedAt(now);
		newLibrary.setModifiedAt(now);

		try {

			LOG.debug("create library");
			return librariesRepository.create(newLibrary);
		} catch (Exception e) {
			throw new RuntimeException("fallimento creazione libreria db", e);
		}
	}
	
	public List<Library> getUserLibraries(String userId){
	    LOG.debug("get user libraries");
	    
	    FindIterable<Library> found = librariesRepository.getAll(userId);
	    
	    if (found == null) {
	        LOG.warnf("Il repository ha restituito un risultato nullo per userId: %s. Ritorno lista vuota.", userId);
	        return Collections.emptyList();
	    }
	    
	    // conversione da FindIterable a List se e' vuoto, restituisce una lista vuota
	    return found.into(new java.util.ArrayList<>());
	}

	public Library getLibraryDetail(String libraryId, String currentUserId) {
		
	    LOG.debug("recupero dettaglio libreria: " + libraryId);

	    Optional<Library> opLib = librariesRepository.get(libraryId);
	    if (opLib.isEmpty()) {
	        return null;
	    }

	    Library lib = opLib.get();
	    boolean isOwner = lib.getOwnerId().equals(currentUserId);

	    // gestione visibilita
	    if (!isOwner && "private".equals(lib.getVisibility())) {
	        return null; // libreria privata
	    }
	
	    return lib;
	}
}