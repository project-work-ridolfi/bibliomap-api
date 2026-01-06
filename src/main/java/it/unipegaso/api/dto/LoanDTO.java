package it.unipegaso.api.dto;

import java.util.Date;

public record LoanDTO(
	String id,
	String title,
	String requesterId,
	String ownerId,
	String copyId, 
	String status, 
	Date loanStartDate, 
	Date expectedReturnDate,
	String ownerNotes, 
	String ownerUsername, 
	String requesterUsername,
	Date updatedAt) {
}
