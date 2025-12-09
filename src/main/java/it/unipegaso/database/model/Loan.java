package it.unipegaso.database.model;

import java.util.Date;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

public class Loan {
	
	@BsonId
	private String id;
	
	private String title;
	
	@BsonProperty("requester_id")
	private String requesterId;
	
	@BsonProperty("owner_id")
	private String ownerId;
	
	@BsonProperty("copy_id")
	private String copyId;
	
	private String status;
	
	@BsonProperty("created_at")
	private Date createdAt;
	
	@BsonProperty("updated_at")
	private Date updatedAt;
	
	@BsonProperty("loan_start_date")
	private Date loanStartDate;
	
	@BsonProperty("expected_return_date")
	private Date expectedReturnDate;
	
	@BsonProperty("actual_return_date")
	private Date actualReturnDate;
	
	@BsonProperty("owner_notes")
	private String ownerNotes;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getRequesterId() {
		return requesterId;
	}
	public void setRequesterId(String requesterId) {
		this.requesterId = requesterId;
	}
	public String getOwnerId() {
		return ownerId;
	}
	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}
	public String getCopyId() {
		return copyId;
	}
	public void setCopyId(String copyId) {
		this.copyId = copyId;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public Date getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}
	public Date getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}
	public Date getLoanStartDate() {
		return loanStartDate;
	}
	public void setLoanStartDate(Date loanStartDate) {
		this.loanStartDate = loanStartDate;
	}
	public Date getExpectedReturnDate() {
		return expectedReturnDate;
	}
	public void setExpectedReturnDate(Date expectedReturnDate) {
		this.expectedReturnDate = expectedReturnDate;
	}
	public Date getActualReturnDate() {
		return actualReturnDate;
	}
	public void setActualReturnDate(Date actualReturnDate) {
		this.actualReturnDate = actualReturnDate;
	}
	public String getOwnerNotes() {
		return ownerNotes;
	}
	public void setOwnerNotes(String ownerNotes) {
		this.ownerNotes = ownerNotes;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	
}
