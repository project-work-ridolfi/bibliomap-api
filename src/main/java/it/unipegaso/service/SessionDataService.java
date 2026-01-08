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


	public void save(String sessionId, Map<String, String> data, int expirationSeconds) {

		String redisKey = redisKey(sessionId);

		// salva i campi della mappa
		for (Map.Entry<String, String> entry : data.entrySet()) {
        	hashCommands.hset(redisKey, entry.getKey(), entry.getValue());
    	}
		// imposta ttl 
		keyCommands.expire(redisKey, expirationSeconds);

	}


	public Optional<String> get(String sessionId, String field) {

		String value = hashCommands.hget(redisKey(sessionId), field);

		return Optional.ofNullable(value);
	}


	public Map<String, String> getAll(String sessionId) {

		return hashCommands.hgetall(redisKey(sessionId));
	}


	public void delete(String sessionId) {
		keyCommands.del(redisKey(sessionId));
	}


	public void updateField(String sessionId, String field, String value) {
		hashCommands.hset(redisKey(sessionId), field, value);
	}

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
