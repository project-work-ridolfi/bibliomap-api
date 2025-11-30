package it.unipegaso.service;

import org.jboss.logging.Logger;

import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Position;

import it.unipegaso.api.dto.SetLocationDTO;
import it.unipegaso.database.LocationsRepository;
import it.unipegaso.database.model.Location;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LocationService {

	private static final Logger LOG = Logger.getLogger(LocationService.class); 

	@Inject
	LocationsRepository locationRepository;

	// Helper per la conversione da coordinate a oggetto GeoJSON Point.
	private Point createGeoJsonPoint(SetLocationDTO dto) {
		// Logica di conversione e gestione errori
		double lat = dto.latitude();
		double lng = dto.longitude();
		// GeoJSON standard: [LONGITUDINE, LATITUDINE]
		return new Point(new Position(lng, lat));
	}


	public String saveNewLocation(SetLocationDTO dto) {

		Point geoPoint = createGeoJsonPoint(dto);

		Location newLocation = new Location();
		newLocation.location = geoPoint;

		String id = null;

		try {
			id = locationRepository.create(newLocation);

		}catch(Exception e) {
			LOG.error("Impossibile inserire location nel database");
		}

		return id;
	}
}
