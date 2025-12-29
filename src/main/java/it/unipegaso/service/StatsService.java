package it.unipegaso.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.unipegaso.api.dto.ChartData;
import it.unipegaso.api.dto.UserStatsDTO;
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

    public UserStatsDTO getAllUserStats(String userId) {
        
    	// Conteggi base
	    long myBooks = libraryService.countCopies(userId, true);
        long loansOut = loansRepository.count(userId, true);
        long loansIn = loansRepository.count(userId, false);

        // Analisi Personali
        String topTag = copiesRepository.findTopTagByUser(userId);
        String partner = loansRepository.findTopPartner(userId);
        
        // Calcolo Distanza Massima Geografica
        double maxDist = calculateRealMaxDistance(userId);

        // Preparazione Grafici
        ChartData trend = mapToChartData(loansRepository.getMonthlyTrend(userId));
        ChartData weekly = mapToChartData(loansRepository.getWeeklyRequests(userId));
        ChartData pareto = mapToChartData(loansRepository.getTitlesRanking(userId));
        ChartData requesters = mapToChartData(loansRepository.getTopRequesters(userId));

        return new UserStatsDTO(
            myBooks, loansIn, loansOut, 
            topTag, Math.round(maxDist * 100.0) / 100.0, partner,
            trend, weekly, pareto, requesters
        );
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
}