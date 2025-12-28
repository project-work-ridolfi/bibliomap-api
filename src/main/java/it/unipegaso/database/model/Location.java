package it.unipegaso.database.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.mongodb.client.model.geojson.Point;

public class Location {
	
	@BsonId
	private String id; 

	// Oggetto GeoJSON standard di MongoDB per il campo indicizzato '2dsphere'
    @BsonProperty("geolocation")
    private Point location;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Point getLocation() {
		return location;
	}

	public void setLocation(Point location) {
		this.location = location;
	}
    
    
}
