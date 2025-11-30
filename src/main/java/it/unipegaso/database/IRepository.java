package it.unipegaso.database;

import java.util.Optional;

import com.mongodb.MongoWriteException;

public interface IRepository<T> {
	
    public static final String ID = "_id";
	
	String create(T obj) throws MongoWriteException;
	
	Optional<T> get(String id);
	
	boolean update (T obj) throws MongoWriteException;
	
	boolean delete (String id);

}
