package it.unipegaso.service;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import it.unipegaso.service.otp.CalculatedSecretKeyStrategy;
import it.unipegaso.service.otp.CounterStrategy;
import it.unipegaso.service.otp.DecodingException;
import it.unipegaso.service.otp.HMACSHA1OTPValidator;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OtpService {

    private static final Logger LOG = Logger.getLogger(OtpService.class);
    
    private static final String RETRY_FIELD_NAME = "otp_retries"; 
    private static final int CODE_DIGITS = 6;
    private static final boolean ADD_CHECKSUM = false;
    private static final int TRUNCATION_OFFSET = 16;

    @Inject
    RedisDataSource ds; 
    
    @Inject
    SessionDataService sessionDataService; 

    private ValueCommands<String, String> valueCommands; 
    private KeyCommands<String> keyCommands;

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "quarkus.auth.otp.duration-minutes", defaultValue = "5")
    long otpDurationMinutes;
    
    @ConfigProperty(name = "quarkus.auth.otp.max-retries", defaultValue = "3")
    long maxRetriesConfig; 

    @ConfigProperty(name = "quarkus.email.debug-mode", defaultValue = "false")
    boolean otpDebugMode;


    private final HMACSHA1OTPValidator otpValidator = new HMACSHA1OTPValidator();
    private final CounterStrategy counterStrategy = new CounterStrategy();
    private final CalculatedSecretKeyStrategy secretKeyStrategy = new CalculatedSecretKeyStrategy();

    @PostConstruct
    void init() {
        this.valueCommands = ds.value(String.class, String.class);
        this.keyCommands = ds.key(String.class);
    }
    
    public boolean isOtpDebugMode() {
    	return otpDebugMode;
    }

    public String generateAndSendOtp(String email, String sessionId, String username) {
        
    	String secret = secretKeyStrategy.load(email + sessionId);
        long counter = counterStrategy.getCounter(); // Ottiene il counter basato sul tempo

        try {
            String otp = otpValidator.generatePassword(
                secret, 
                counter, 
                CODE_DIGITS, 
                ADD_CHECKSUM, 
                TRUNCATION_OFFSET
            );
            
            long expiration = otpDurationMinutes * 60; 
            
            // Salva il counter su Redis
            valueCommands.setex(email, expiration, "" + counter); 
            
            sessionDataService.updateLong(sessionId, RETRY_FIELD_NAME, maxRetriesConfig);
            
            if (otpDebugMode) {
                return otp;
            } else {
                boolean success = emailService.sendOtpEmail(email, otp, username);
                LOG.info("SEND EMAIL " + success);
                if (success) {
                    return otp; 
                } else {
                    keyCommands.del(email);
                    return null; 
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | DecodingException e) {
            LOG.error("Failed to generate OTP", e);
            return null;
        }
    }



    /**
     * Tenta la verifica OTP, ritorna lo stato completo (inclusi i tentativi).
     * @return Una mappa con lo stato: {"valid": true/false, "retriesRemaining": X, "isBlocked": true/false}
     */
    public Map<String, Object> verifyOtp(String email, String otpCode, String sessionId) {

        // Recupero stato tentativi
        Optional<Long> retriesOpt = sessionDataService.getLong(sessionId, RETRY_FIELD_NAME);
        long retriesRemaining = retriesOpt.orElse(maxRetriesConfig); // Valore di default
        boolean isBlocked = retriesRemaining <= 0;
        
        // Controllo preliminare di blocco
        if (retriesRemaining <= 0) {
            // La sessione è bloccata. Non consente ulteriori tentativi.
            return buildResult(false, 0, true, "Account temporaneamente bloccato. Richiedi un nuovo codice.");
        }
        
        // Recupera il counter salvato su Redis 
        String storedCounter = valueCommands.get(email);
        
        // Inizializzazione degli stati di verifica
        boolean isExpired = storedCounter == null;
        boolean isCodeMatch = false;

        if (!isExpired) {
            try {
            	
                long counter = Long.valueOf(storedCounter);
                // Ricostruzione: usa il counter STORICO per la verifica
                String secret = secretKeyStrategy.load(email + sessionId);
                
                String expectedOtp = otpValidator.generatePassword(
                    secret, 
                    counter,
                    CODE_DIGITS, 
                    ADD_CHECKSUM, 
                    TRUNCATION_OFFSET
                );
                
                isCodeMatch = expectedOtp.equals(otpCode);
                
            } catch (Exception e) {
                LOG.error("Errore critico nella ricostruzione/parsing OTP da Redis.", e);
                // Non decrementa i tentativi per un errore interno al server
                return buildResult(false, retriesRemaining, false, "Errore interno durante la verifica.");
            }
        }
        
        // Gestione del Risultato e Aggiornamento Atomico
        if (isCodeMatch && !isExpired) {
            // SUCCESSO: OTP VALIDO
            keyCommands.del(email); // Uso singolo
            sessionDataService.deleteField(sessionId, RETRY_FIELD_NAME); 
            
            return buildResult(true, maxRetriesConfig, false, "Verifica OTP riuscita.");

        } else {
            // FALLIMENTO: Codice sbagliato o scaduto
            
            // Decremento atomico del contatore dei retry. 
            long retriesAfterAttempt = sessionDataService.incrementBy(sessionId, RETRY_FIELD_NAME, -1);
            retriesRemaining = retriesAfterAttempt;
            isBlocked = retriesAfterAttempt <= 0;

            String message;
            if (isExpired) {
                 message = "Codice OTP scaduto o non trovato. Richiedi un nuovo codice.";
            } else if (isBlocked) {
                message = "Tentativi esauriti. Richiedi un nuovo codice.";
            } else {
                message = "Codice non valido. Riprova.";
            }
            
            return buildResult(false, retriesRemaining, isBlocked, message);
        }
    }
    
    // Helper per costruire la risposta
    private Map<String, Object> buildResult(boolean valid, long retries, boolean blocked, String message) {
        return Map.of(
            "valid", valid,
            "retriesRemaining", retries,
            "isBlocked", blocked,
            "message", message
        );
    }
}