package it.unipegaso.database.model;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class User {
	public String id; 
    public String username;
    public String email;
    public String hashedPassword;
    public boolean acceptedTerms; 
    public LocalDateTime createdAt; 
    public LocalDateTime modifiedAt;
    public String locationId; 
    public String visibility; //all, logged-in, no one
    public int blurRadius;
    public List<String> collections;
}
