package it.unipegaso.database;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

}
