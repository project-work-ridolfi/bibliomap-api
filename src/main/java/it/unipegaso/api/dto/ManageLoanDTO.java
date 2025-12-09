package it.unipegaso.api.dto;

public record ManageLoanDTO(
		String requesterId,
		String action,
		String loanId,
		String copyId,
		String notes) {}
