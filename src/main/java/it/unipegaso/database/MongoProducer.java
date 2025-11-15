package it.unipegaso.database;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import it.unipegaso.database.model.Location;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject; 

@ApplicationScoped
public class MongoProducer {

    private static final Logger LOG = Logger.getLogger(MongoProducer.class);
    
    @Inject 
    MongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "bibliomap")
    String databaseName;

    @Produces
    public MongoCollection<User> users() { 
        
        MongoDatabase database = mongoClient.getDatabase(databaseName); 
        
        MongoCollection<User> collection = database.getCollection("users", User.class);
        
        LOG.infof("Producer creato per la collection users: %s.%s", database.getName(), collection.getNamespace().getCollectionName());
        
        return collection;
    }
    
    @Produces
    public MongoCollection<Location> locations() { 
        
        MongoDatabase database = mongoClient.getDatabase(databaseName); 
        
        MongoCollection<Location> collection = database.getCollection("locations", Location.class);
        
        LOG.infof("Producer creato per la collection locations: %s.%s", database.getName(), collection.getNamespace().getCollectionName());
        
        return collection;
    }
    
}