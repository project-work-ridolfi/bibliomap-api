package it.unipegaso.database.model;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Copy {
	
	@BsonId
	public String id; 
	
	public String libraryId;
	
	@BsonProperty("book_isbn")
    public String bookIsbn;
	public String status;
	public String condition;
	
	@BsonProperty("owner_notes")
    public String ownerNotes;
	
	@BsonProperty("custom_cover")
    public String customCover;
	
	public List<String> tags;

}
