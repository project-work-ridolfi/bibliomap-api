package it.unipegaso.database;

import java.util.Optional;

import org.bson.conversions.Bson;

import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;

import it.unipegaso.database.model.Loan;

public class LoanRepository implements IRepository<Loan> {

	@Override
	public String create(Loan obj) throws MongoWriteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Optional<Loan> get(String id) {
		// TODO Auto-generated method stub
		return Optional.empty();
	}

	@Override
	public boolean update(Loan obj) throws MongoWriteException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean delete(String id) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public FindIterable<Loan> find(Bson filter) {
		// TODO Auto-generated method stub
		return null;
	}

}
