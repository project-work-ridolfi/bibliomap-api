package it.unipegaso.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.bson.Document;
import org.jboss.logging.Logger;

import jakarta.inject.Inject; 
import org.eclipse.microprofile.config.inject.ConfigProperty; 

@ApplicationScoped
public class MongoProducer {

    private static final Logger LOG = Logger.getLogger(MongoProducer.class);
    
    @Inject 
    MongoClient mongoClient;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "bibliomap")
    String databaseName;

    @Produces
    public MongoCollection<Document> users() {
        
        MongoDatabase database = mongoClient.getDatabase(databaseName); 
        MongoCollection<Document> collection = database.getCollection("users");
        LOG.infof("Producer creato per la collection: %s.%s", database.getName(), collection.getNamespace().getCollectionName());
        
        return collection;
    }
}