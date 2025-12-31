package it.unipegaso.database.model;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Copy {
	
	@BsonId
	private String id; 
	
	private String libraryId;
	
	@BsonProperty("book_isbn")
	private String bookIsbn;
	private String status;
	private String condition;
	
	@BsonProperty("owner_notes")
	private String ownerNotes;
	
	@BsonProperty("custom_cover")
	private String customCover;
	
	@BsonProperty("views_counter")
	private long viewsCounter;
	
	private List<String> tags;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLibraryId() {
		return libraryId;
	}

	public void setLibraryId(String libraryId) {
		this.libraryId = libraryId;
	}

	public String getBookIsbn() {
		return bookIsbn;
	}

	public void setBookIsbn(String bookIsbn) {
		this.bookIsbn = bookIsbn;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public String getOwnerNotes() {
		return ownerNotes;
	}

	public void setOwnerNotes(String ownerNotes) {
		this.ownerNotes = ownerNotes;
	}

	public String getCustomCover() {
		return customCover;
	}

	public void setCustomCover(String customCover) {
		this.customCover = customCover;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public long getViewsCounter() {
		return viewsCounter;
	}

}
