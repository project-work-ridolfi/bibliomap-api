package it.unipegaso.api.dto;

public record GlobalStatsDTO(
	    long totalBooks,
	    long totalCopies,
	    long totalLoans,
	    String topTag,
	    double maxDistance,
	    String topRequester,
	    String topLoaner,
	    ChartData loansTrend,
	    ChartData weeklyRequests,
	    ChartData paretoBooks) {

}
