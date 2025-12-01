package it.unipegaso.database.model;

import org.bson.codecs.pojo.annotations.BsonId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Book {

	@BsonId
	public String isbn; 
	
	public String author;
	public String title;
	public String language;
	public int publication_year;
	public String publisher;
	public String cover_type;
	public String cover; //TODO
}
