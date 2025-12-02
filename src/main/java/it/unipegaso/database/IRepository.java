package it.unipegaso.database;

import java.util.Optional;

import org.bson.conversions.Bson;

import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;

public interface IRepository<T> {
	
    public static final String ID = "_id";
	
	String create(T obj) throws MongoWriteException;
	
	Optional<T> get(String id);
	
	boolean update (T obj) throws MongoWriteException;
	
	boolean delete (String id);
	
	FindIterable<T> find(Bson filter);

}
