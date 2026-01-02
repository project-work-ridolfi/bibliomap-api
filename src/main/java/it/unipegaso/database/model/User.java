package it.unipegaso.database.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class User {
	
	@BsonId
	private String id; 
    
	private String username;
	private String email;
	private String hashedPassword;
	private boolean acceptedTerms; 
	private Date createdAt; 
	private Date modifiedAt;
	private String locationId; 
	private String visibility; //all, logged-in, no one
	private int blurRadius;
	private List<String> collections;
	private List<Map<String, Object>> history;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getHashedPassword() {
		return hashedPassword;
	}
	public void setHashedPassword(String hashedPassword) {
		this.hashedPassword = hashedPassword;
	}
	public boolean isAcceptedTerms() {
		return acceptedTerms;
	}
	public void setAcceptedTerms(boolean acceptedTerms) {
		this.acceptedTerms = acceptedTerms;
	}
	public Date getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}
	public Date getModifiedAt() {
		return modifiedAt;
	}
	public void setModifiedAt(Date modifiedAt) {
		this.modifiedAt = modifiedAt;
	}
	public String getLocationId() {
		return locationId;
	}
	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}
	public String getVisibility() {
		return visibility;
	}
	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}
	public int getBlurRadius() {
		return blurRadius;
	}
	public void setBlurRadius(int blurRadius) {
		this.blurRadius = blurRadius;
	}
	public List<String> getCollections() {
		return collections;
	}
	public void setCollections(List<String> collections) {
		this.collections = collections;
	}
	public List<Map<String, Object>> getHistory() {
		return history;
	}
	public void setHistory(List<Map<String, Object>> history) {
		this.history = history;
	}
	public void addToHistory(Map<String, Object> map) {
		if(history == null) {
			history = new ArrayList<>();
		}
		
		history.add(map);
	}
	
}
