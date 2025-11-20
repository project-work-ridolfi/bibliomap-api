package it.unipegaso.service;

import java.time.LocalDateTime;

import org.jboss.logging.Logger;

import it.unipegaso.api.dto.LibraryDTO;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.User;
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
	 * @param sessionId id sessione
	 * @param libraryDTO dati libreria
	 * @return id libreria creata
	 */
	public String createNewLibrary(String userId, LibraryDTO libraryDTO) {

		LOG.info("create new library init");

		// RECUPERO UTENTE 
		User user = usersRepository.get(userId).orElseThrow(() -> new IllegalStateException("utente non trovato db"));


		LOG.info("username: " + user.username);

		Library newLibrary = new Library();
		newLibrary.name = libraryDTO.name();
		newLibrary.ownerId = user.id;
		newLibrary.visibility = libraryDTO.visibility();

		LOG.info("library creata");

		// collegamento della posizione del profilo
		if ("user_default".equals(libraryDTO.locationType()) && user.locationId != null) {

			LOG.info("user default");
			newLibrary.locationId = user.locationId;
		} 
		// TODO: Gestire "new_location" navigando a un altro endpoint di creazione posizione.

		LocalDateTime now = LocalDateTime.now();
		newLibrary.createdAt = now;
		newLibrary.modifiedAt = now;

		try {

			LOG.info("create library");
			return librariesRepository.create(newLibrary);
		} catch (Exception e) {
			throw new RuntimeException("fallimento creazione libreria db", e);
		}
	}
}