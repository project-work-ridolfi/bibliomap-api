package it.unipegaso.database.model;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Copy {
	
	@BsonId
	public String id; 
	
	public String libraryId;
	public String book_isbn;
	public String status;
	public String condition;
	public String owner_notes;
	public List<String> tags;

}
