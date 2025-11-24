package it.unipegaso.service;

import java.util.Map;
import java.util.Optional;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SessionDataService {

	@Inject
	RedisDataSource ds; 

	private HashCommands<String, String, String> hashCommands; 
	private HashCommands<String, String, Long> hashLongCommands;
	private KeyCommands<String> keyCommands;

	@Inject
	void setCommands() {
		this.hashCommands = ds.hash(String.class);
		this.hashLongCommands = ds.hash(Long.class);
		this.keyCommands = ds.key(String.class);
	}

	/**
	 * Salva tutti i dati di una mappa come campi hash sotto l'unica chiave sessionId in Redis.
	 * @param sessionId L'ID della sessione (la chiave principale in Redis).
	 * @param data La mappa contenente tutti i dati (es. {"email": "a", "username": "b"}).
	 * @param expirationSeconds la durata in secondi della sessione
	 */
	public void save(String sessionId, Map<String, String> data, int expirationSeconds) {

		String redisKey = redisKey(sessionId);


		// salva i campi della mappa
		hashCommands.hset(redisKey, data); 

		// imposta ttl 
		keyCommands.expire(redisKey, expirationSeconds);

	}

	/**
	 * Recupera un singolo valore (campo) dall'hash legato alla sessione.
	 * @param sessionId L'ID della sessione.
	 * @param field Il nome del campo (es. "username", "email").
	 * @return Il valore del campo, se presente.
	 */
	public Optional<String> get(String sessionId, String field) {

		String value = hashCommands.hget(redisKey(sessionId), field);

		return Optional.ofNullable(value);
	}

	/**
	 * Recupera tutti i dati di sessione (la mappa completa).
	 * @param sessionId L'ID della sessione.
	 * @return La Map completa dei dati di sessione.
	 */
	public Map<String, String> getAll(String sessionId) {

		return hashCommands.hgetall(redisKey(sessionId));
	}

	/**
	 * Cancella tutti i dati di sessione
	 * @param sessionId L'ID della sessione.
	 */
	public void delete(String sessionId) {
		
		keyCommands.del(redisKey(sessionId));
		
	}
	
	/**
	 * Aggiorna un singolo dato nella mappa
	 * @param sessionId L'ID della sessione.
	 * @param field la chiave del campo da aggiornare
	 * @parma value il nuovo valore
	 */
	public void updateField(String sessionId, String field, String value) {
	    hashCommands.hset(redisKey(sessionId), field, value);
	}
	
	/**
	 * Decrementa/Incrementa in modo atomico un campo numerico (HINCRBY).
	 * @param sessionId L'ID della sessione.
	 * @param field La chiave del campo numerico
	 * @param amount La quantità da aggiungere
	 * @return Il nuovo valore del campo.
	 */
	public long incrementBy(String sessionId, String field, long amount) {
	    String key = redisKey(sessionId);
	    
	    // Utilizza l'istanza specializzata per HINCRBY
	    return hashLongCommands.hincrby(key, field, amount);
	}
	
	public Optional<Long> getLong(String sessionId, String field){
		Long value = hashLongCommands.hget(redisKey(sessionId), field);
		return Optional.ofNullable(value);
	}
	
	public void updateLong(String sessionId, String field, long value) {
		hashLongCommands.hset(redisKey(sessionId), field, value);
	}
	
	public void deleteField(String sessionId, String field) {
		hashCommands.hdel(redisKey(sessionId), field);
	}
	

	private String redisKey(String sessionId) {
		return "sid_" + sessionId;
	}


}