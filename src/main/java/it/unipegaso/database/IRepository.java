package it.unipegaso.database;

import com.mongodb.MongoWriteException;

public interface IRepository<T> {
	
	
	String create(T obj) throws MongoWriteException;

}
