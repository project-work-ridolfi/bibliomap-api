package it.unipegaso.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import it.unipegaso.api.dto.ChartData;
import it.unipegaso.api.dto.GlobalStatsDTO;
import it.unipegaso.api.dto.UserStatsDTO;
import it.unipegaso.database.BooksRepository;
import it.unipegaso.database.CopiesRepository;
import it.unipegaso.database.LibrariesRepository;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.LocationsRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Library;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class StatsService {


	@Inject 
	CopiesRepository copiesRepository;

	@Inject 
	LoansRepository loansRepository;

	@Inject 
	LibrariesRepository librariesRepository;

	@Inject 
	UsersRepository usersRepository;

	@Inject 
	LocationsRepository locationsRepository;

	@Inject
	LibraryService libraryService;

	@Inject
	BooksRepository booksRepository;

	@Inject
	MongoClient mongoClient;

	private static final Logger LOG = Logger.getLogger(StatsService.class);

	public UserStatsDTO getAllUserStats(String userId, boolean isOwner, boolean isLogged) {

		LOG.debug("******** USER STATS ************");
		// Conteggi base
		long myBooks = libraryService.countCopies(userId, true);
		long loansOut = loansRepository.count(userId, true);
		long loansIn = loansRepository.count(userId, false);

		// Analisi Personali

		List<String> userLibIds = librariesRepository.getUserLibIds(userId, isLogged, isOwner);
		String topTag = copiesRepository.findTopTagByLibraryIds(userLibIds);

		String bffId = loansRepository.findTopPartnerId(userId);
		
		String partner = "nessuno";
		
		if(bffId != null) {
			
			Optional<User> op = usersRepository.get(bffId);
			
			if(op.isPresent()) {
				
				User user = op.get();
				
				partner = user.getUsername() + "_" + user.getId();
			}
			
		}

		// Calcolo Distanza Massima Geografica
		double maxDist = calculateRealMaxDistance(userId);
		
		// Preparazione Grafici
		ChartData trend = mapToChartData(loansRepository.getMonthlyTrend(userId));
		ChartData tags = mapToChartData(copiesRepository.getTags(userLibIds));
		ChartData titlesRanking = mapToChartData(loansRepository.getTitlesRanking(userId));
		ChartData requesters = mapToChartData(loansRepository.getTopRequesters(userId));
		ChartData mostViewedBooks = mapToChartData(calculateMostViewedBooks(userLibIds)); 
		ChartData mostVisitedLibraries = mapToChartData(librariesRepository.getUserLibrariesViewsMap(userId, isOwner, isLogged));

		return new UserStatsDTO(
				myBooks, loansIn, loansOut, 
				topTag, Math.round(maxDist * 100.0) / 100.0, partner,
				trend, tags, titlesRanking, requesters,
				mostViewedBooks, mostVisitedLibraries
				);
	}

	private Map<String, Long> calculateMostViewedBooks(List<String> libIds) {
	    Map<String, Long> rawViews = copiesRepository.getViews(libIds);

	    List<String> isbns = rawViews.keySet().stream()
	            .map(k -> {
	            	LOG.debug("KEY RAW VIEWS " + k );
	                String[] parts = k.split("_");
	                return (parts.length > 1) ? parts[1] : null; 
	            })
	            .filter(isbn -> isbn != null && !isbn.equals("null"))
	            .distinct()
	            .toList();

	    Map<String, String> titlesMap = booksRepository.getTitlesMap(isbns);

	    Map<String, Long> mostViewedBooks = new LinkedHashMap<>();

	    rawViews.forEach((key, views) -> {
	        String[] parts = key.split("_");
	        String copyId = parts[0];
	        String isbn = (parts.length > 1) ? parts[1] : "unknown";

	        String title = titlesMap.getOrDefault(isbn, isbn);
	        String finalKey = copyId + "_" + title;

	        mostViewedBooks.put(finalKey, views);
	    });

	    return mostViewedBooks;
	}

	private double calculateRealMaxDistance(String userId) {
		List<Loan> finishedLoans = loansRepository.findFinishedByOwner(userId);
		double max = 0.0;

		for (Loan loan : finishedLoans) {
			// Recupero location della libreria 
			String ownerLocId = copiesRepository.get(loan.getCopyId())
					.flatMap(copy -> librariesRepository.get(copy.getLibraryId()))
					.map(Library::getLocationId)
					.orElse(null);

			// Recupero location dell'Utente (Richiedente)
			String reqLocId = usersRepository.get(loan.getRequesterId())
					.map(User::getLocationId)
					.orElse(null);

			if (ownerLocId != null && reqLocId != null) {
				double[] c1 = locationsRepository.getCoordinates(ownerLocId);
				double[] c2 = locationsRepository.getCoordinates(reqLocId);

				if (c1 != null && c2 != null) {
					double d = haversine(c1[0], c1[1], c2[0], c2[1]);
					if (d > max) max = d;
				}
			}
		}
		return max;
	}

	private ChartData mapToChartData(Map<String, Long> map) {
		return new ChartData(new ArrayList<>(map.keySet()), new ArrayList<>(map.values()));
	}

	private double haversine(double lat1, double lon1, double lat2, double lon2) {
		double R = 6371; // Raggio terra in km
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
				Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
				Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	public GlobalStatsDTO getGlobalStats(boolean logged, String userId) {

		long totalBooks = booksRepository.count(); 
		long totalCopies = copiesRepository.count(); 
		long totalLoans = loansRepository.count(); 

		String topTag = findTopTagGlobal();
		String topRequester = findTopUser(logged, false, userId);
		String topLoaner = findTopUser(logged, true, userId);

		List<String> visibleLibraryIds = librariesRepository.getVisibleLibraryIds(logged, userId);
		
		// calcolo Distanza Massima Globale (viaggio piu' lungo mai fatto)
		double maxDistGlobal = calculateGlobalMaxDistance();

		// preparazione Grafici 
		ChartData trend = mapToChartData(loansRepository.getMonthlyTrend(null));
		ChartData weekly = mapToChartData(loansRepository.getWeeklyRequests());
		ChartData pareto = mapToChartData(loansRepository.getTitlesRanking(null));
		ChartData mostViewedBooks = mapToChartData(calculateMostViewedBooks(visibleLibraryIds)); 
		ChartData mostVisitedLibraries = mapToChartData(librariesRepository.getAllLibrariesViewsMap(userId, logged));

		return new GlobalStatsDTO(
				totalBooks, totalCopies, totalLoans, 
				topTag, Math.round(maxDistGlobal * 100.0) / 100.0, topRequester, topLoaner,
				trend, weekly, pareto, mostViewedBooks, mostVisitedLibraries
				);
	}

	private String findTopTagGlobal() {


		MongoCollection<Document> copies = mongoClient.getDatabase("bibliomap").getCollection("copies");

		List<Bson> pipeline = List.of(
				Aggregates.unwind("$tags"),
				Aggregates.group("$tags", Accumulators.sum("count", 1)),
				Aggregates.sort(Sorts.descending("count")),
				Aggregates.limit(1)
				);

		Document result = copies.aggregate(pipeline).first();

		return result != null ? result.getString("_id") : null;
	}

	public String findTopUser(boolean logged, boolean owner, String userId) {
	    
		MongoDatabase mongoDatabase = mongoClient.getDatabase("bibliomap");
	    MongoCollection<Document> loans = mongoDatabase.getCollection("loans");

	    List<Bson> pipeline = new ArrayList<>();

	    // prende utente
	    pipeline.add(Aggregates.lookup("users", owner ? "owner_id" : "requester_id", "_id", "user"));
	    pipeline.add(Aggregates.unwind("$user"));

	    //filtra in base alla visibilità
	    List<Bson> visibilityFilters = new ArrayList<>();
	    if (logged) {
	        // se loggato, vedo pubblici, registrati e me stesso
	        visibilityFilters.add(Filters.in("user.visibility", List.of("all", "logged_in")));
	        if (userId != null) {
	            visibilityFilters.add(Filters.eq("user._id", userId));
	        }
	    } else {
	        // se guest, vedo solo i pubblici
	        visibilityFilters.add(Filters.eq("user.visibility", "all"));
	    }
	    pipeline.add(Aggregates.match(Filters.or(visibilityFilters)));

	    // raggruppa per contare i prestiti/richieste degli utenti visibili
	    pipeline.add(Aggregates.group("$_id_dell_utente_o_username", 
	        Accumulators.first("username", "$user.username"),
	        Accumulators.sum("count", 1)));

	    // sort
	    pipeline.add(Aggregates.sort(Sorts.descending("count")));

	    // prende il primo
	    pipeline.add(Aggregates.limit(1));

	    Document result = loans.aggregate(pipeline).first();
	    return result != null ? result.getString("username") : null;
	}
	private double calculateGlobalMaxDistance() {
		List<Loan> finishedLoans = loansRepository.findFinished();
		double max = 0.0;

		for (Loan loan : finishedLoans) {

			String ownerLocId = copiesRepository.get(loan.getCopyId())
					.flatMap(copy -> librariesRepository.get(copy.getLibraryId()))
					.map(Library::getLocationId)
					.orElse(null);

			String reqLocId = usersRepository.get(loan.getRequesterId())
					.map(User::getLocationId)
					.orElse(null);

			if (ownerLocId != null && reqLocId != null) {
				double[] c1 = locationsRepository.getCoordinates(ownerLocId);
				double[] c2 = locationsRepository.getCoordinates(reqLocId);

				if (c1 != null && c2 != null) {
					double d = haversine(c1[0], c1[1], c2[0], c2[1]);
					if (d > max) max = d;
				}
			}
		}
		return max;
	}

}