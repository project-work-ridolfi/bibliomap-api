package it.unipegaso.database.model;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.codecs.pojo.annotations.BsonId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Library {
	
	@BsonId
	public String id; 
    
	public String name;
    public LocalDateTime createdAt; 
    public LocalDateTime modifiedAt;
    public String ownerId; 
    public String locationId; 
    public String visibility; //all, logged-in, no one
    public int blurRadius;
    public String notes; //anche vuoto
    public List<String> tags; //anche vuoto
}
