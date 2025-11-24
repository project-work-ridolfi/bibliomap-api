package it.unipegaso.service;

import java.util.ArrayList;
import java.util.List;

import it.unipegaso.api.dto.BookMapDTO;
import it.unipegaso.database.LocationsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BookService {

    @Inject
    LocationsRepository locationsRepository;

    /**
     * Cerca libri vicini coordinando la richiesta al repository.
     */
    public List<BookMapDTO> findNearbyBooks(double lat, double lng, double radiusKm, String visibilityFilter) {
        
        List<String> allowedVisibilities = new ArrayList<>();
        allowedVisibilities.add("all"); // Sempre visibili
        
        if ("logged-in".equals(visibilityFilter)) {
            allowedVisibilities.add("logged-in");
        }
        
        // Delega tutto al Repository che possiede la collection e la logica di query
        return locationsRepository.findNearbyBooks(lat, lng, radiusKm, allowedVisibilities);
    }
}