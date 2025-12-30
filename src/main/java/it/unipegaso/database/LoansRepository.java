package it.unipegaso.database;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Sorts.ascending;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jboss.logging.Logger;

import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.LoanStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LoansRepository implements IRepository<Loan> {

	private static final Logger LOG = Logger.getLogger(LoansRepository.class);

	private static final String REQUESTER_ID = "requester_id";
	private static final String OWNER_ID = "owner_id";


	@Inject
	MongoCollection<Loan> loans;

	@Override
	public String create(Loan loan) throws MongoWriteException {

		loan.setId( UUID.randomUUID().toString()) ;

		InsertOneResult result = loans.insertOne(loan);

		if (!result.wasAcknowledged()) {
			LOG.error("Inserimento prestito non confermato");
			return null;
		}
		return loan.getId();

	}

	@Override
	public Optional<Loan> get(String id) {
		if (id == null || id.trim().isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(loans.find(Filters.eq(ID, id)).first());
	}

	@Override
	public boolean update(Loan loan) throws MongoWriteException {

		if (loan == null || loan.getId().isEmpty()) {
			return false;
		}

		Date now = new Date();
		loan.setUpdatedAt(now);

		UpdateResult result = loans.replaceOne(Filters.eq(ID, loan.getId()), loan);

		return result.getMatchedCount() == 1;
	}

	@Override
	public boolean delete(String id) {
		if (id == null || id.trim().isEmpty()) {
			return false;
		}

		DeleteResult result = loans.deleteOne(Filters.eq(ID, id));

		return result.wasAcknowledged();
	}


	@Override
	public FindIterable<Loan> find(Bson filter) {
		return loans.find(filter);
	}

	public List<Loan> findActiveByUser(String id) {
		// filtro per prestiti in corso dove l'utente e' coinvolto (sia come richiedente che come proprietario)
		Bson filter = Filters.and(
				Filters.or(Filters.eq(REQUESTER_ID, id), Filters.eq(OWNER_ID, id)),
				Filters.eq("status", LoanStatus.ON_LOAN.toString())
				);

		return loans.find(filter).into(new ArrayList<>());
	}

	public List<Loan> findAllUserLoans(String id) {
		// filtro per prestiti dove l'utente e' coinvolto (sia come richiedente che come proprietario)
		Bson filter = Filters.or(Filters.eq(REQUESTER_ID, id), Filters.eq(OWNER_ID, id));

		return loans.find(filter).into(new ArrayList<>());
	}

	public List<Loan> findIncomingByOwner(String id) {
		// filtro per richieste pendenti ricevute dal proprietario
		Bson filter = Filters.and(
				Filters.eq(OWNER_ID, id), 
				Filters.eq("status", LoanStatus.PENDING.toString())
				);

		return loans.find(filter).into(new ArrayList<>());
	}

	public List<Loan> findOverdue(Date date) {
		// stato ON_LOAN e data di ritorno prevista minore di adesso
		Bson filter = Filters.and(
				Filters.eq("status", LoanStatus.ON_LOAN.toString()),
				Filters.lt("expected_return_date", date)
				);

		List<Loan> result = new ArrayList<>();
		find(filter).forEach(result::add);
		return result;
	}

	public void deletePendingByUserId(String userId) {
		Bson filter = Filters.and(
				Filters.eq("status", LoanStatus.PENDING.toString()), 
				Filters.or(Filters.eq(OWNER_ID, userId), Filters.eq(REQUESTER_ID, userId))
				);

		loans.deleteMany(filter);

	}

	public long count( String userId, boolean isOwner) {

		Bson filter;

		if(isOwner) {
			filter = Filters.eq(OWNER_ID, userId);
		}else {
			filter = Filters.eq(REQUESTER_ID, userId);
		}

		return loans.countDocuments(filter);

	}

	@Override
	public long count() {
		return loans.countDocuments();
	}

	public Map<String, Long> getTitlesRanking(String userId) {
	    List<Bson> pipeline = new ArrayList<>();

	    //null per conteggi globali
	    if (userId != null) {
	        pipeline.add(match(eq(OWNER_ID, userId)));
	    }

	    // Concateniamo copy_id e title usando l'operatore $concat di MongoDB
	    // "ID_COPIA:TITOLO_LIBRO"
	    pipeline.add(group(
	        new Document("$concat", Arrays.asList("$copy_id", ":", "$title")), 
	        sum("count", 1)
	    ));

	    pipeline.add(sort(descending("count")));
	    pipeline.add(limit(5));

	    Map<String, Long> ranking = new LinkedHashMap<>();

	    loans.withDocumentClass(Document.class).aggregate(pipeline).forEach(doc -> {
	        String combinedKey = doc.getString("_id");
	        Number count = doc.get("count", Number.class);
	        ranking.put(combinedKey, count.longValue());
	    });

	    return ranking;
	}


	public Map<String, Long> getTopRequesters(String userId) {
		List<Bson> pipeline = Arrays.asList(
				match(eq("ownerId", userId)),
				group("$requesterUsername", sum("count", 1)), // usiamo lo username per comodità del grafico
				sort(descending("count")),
				limit(5)
				);

		Map<String, Long> topUsers = new LinkedHashMap<>();
		loans.withDocumentClass(Document.class)
		.aggregate(pipeline)
		.forEach(doc -> {
			Number count = doc.get("count", Number.class);
			topUsers.put(doc.getString("_id"), count.longValue());
		});
		return topUsers;
	}

	// Trova l'utente con cui l'utente ha scambiato più libri
	public String findTopPartnerId(String userId) {
	    List<Bson> pipeline = Arrays.asList(
	        match(Filters.eq(OWNER_ID, userId)),
	        group("$requester_id", sum("count", 1)),
	        sort(descending("count")),
	        limit(1)
	    );
	    Document res = loans.withDocumentClass(Document.class).aggregate(pipeline).first();
	    
	    // Ritorna l'ID (che è dentro _id dopo il group) o null
	    return res != null ? res.getString("_id") : null;
	}

	// prende tutti i prestiti completati dall'utente
	public List<Loan> findFinishedByOwner(String userId) {
		Bson filter = Filters.and(
				Filters.eq(OWNER_ID, userId),
				Filters.eq("status", LoanStatus.RETURNED.toString()) 
				);
		return loans.find(filter).into(new ArrayList<>());
	}


	// prende tutti i prestiti completati 
	public List<Loan> findFinished() {

		return loans.find(Filters.eq("status", LoanStatus.RETURNED.toString())).into(new ArrayList<>());

	}

	public Map<String, Long> getMonthlyTrend(String ownerId) {

		List<Bson> pipeline = new ArrayList<>();

		// ownerId e' passato a null per i globali
		if (ownerId != null) {
			pipeline.add(match(Filters.eq(OWNER_ID, ownerId)));
		}

		pipeline.add(group(new Document("month", new Document("$month", "$loan_start_date"))
				.append("year", new Document("$year", "$loan_start_date")),
				sum("count", 1)
				)
				);

		pipeline.add(sort(ascending("_id.year", "_id.month")));

		pipeline.add(limit(6));

		Map<String, Long> trend = new LinkedHashMap<>();

		loans.withDocumentClass(Document.class)
		.aggregate(pipeline)
		.forEach(doc -> {
			Document id = doc.get("_id", Document.class);
			if (id.getInteger("month") != null && id.getInteger("year") != null) {
                String label = id.getInteger("month") + "/" + id.getInteger("year");
                trend.put(label, doc.getInteger("count").longValue());
                LOG.debug("label: " +  label + " - count " + doc.getInteger("count").longValue());
            }
		});

		return trend;
	}

	public Map<String, Long> getWeeklyRequests(String userId) {

		List<Bson> pipeline = new ArrayList<>();

		// null per conteggi globali
		if (userId != null) {
			pipeline.add(match(Filters.eq(OWNER_ID, userId)));
		}

		pipeline.add(group(new Document("$week", "$createdAt"), sum("count", 1)));

		pipeline.add(sort(descending("_id")));

		pipeline.add(limit(4));

		Map<String, Long> weekly = new LinkedHashMap<>();

		loans.withDocumentClass(Document.class).aggregate(pipeline).forEach(
				doc -> weekly.put("Settimana " + doc.get("_id"), doc.getInteger("count").longValue()));

		return weekly;
	}



}
