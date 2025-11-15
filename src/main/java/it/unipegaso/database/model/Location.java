package it.unipegaso.database.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.mongodb.client.model.geojson.Point;

public class Location {
	
	@BsonId
	public String id; 

	// Oggetto GeoJSON standard di MongoDB per il campo indicizzato '2dsphere'
    @BsonProperty("geolocation")
    public Point location;
}
