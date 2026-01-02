package it.unipegaso.api.dto;

public record UserStatsDTO(
	    long myBooksCount,
	    long totalLoansIn,
	    long totalLoansOut,
	    String topTag,
	    double maxDistance,
	    String topPartner,
	    ChartData loansTrend,
	    ChartData tags,
	    ChartData titlesRanking,
	    ChartData topRequesters,
	    ChartData mostViewedBooks,
	    ChartData mostVisitedLibraries
	) {}
