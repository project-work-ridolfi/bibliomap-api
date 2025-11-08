package it.unipegaso.service;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.redis.datasource.keys.KeyCommands; 
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

    @Inject
    RedisDataSource ds; 

    // Per i comandi di valore (GET, SETEX)
    private ValueCommands<String, String> valueCommands; 
    
    // Per i comandi sulle chiavi (DEL)
    private KeyCommands<String> keyCommands;

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "quarkus.auth.otp.duration-minutes", defaultValue = "5")
    long otpDurationMinutes;

    @ConfigProperty(name = "quarkus.auth.otp.debug-mode", defaultValue = "false")
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

    public String generateAndSendOtp(String email, String sessionId) {
        String secret = secretKeyStrategy.load(email + sessionId);
        long counter = counterStrategy.getCounter();
        try {
            String otp = otpValidator.generatePassword(secret, counter);
            long expiration = otpDurationMinutes * 60; 
            
            valueCommands.setex(email, expiration, otp); 
            
            if (otpDebugMode) {
                return otp;
            } else {
                emailService.sendOtpEmail(email, otp);
                return null;
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | DecodingException e) {
            LOG.error("Failed to generate OTP", e);
            return null;
        }
    }

    public boolean verifyOtp(String email, String otp) {

    	String storedOtp = valueCommands.get(email); 
        
        if (storedOtp != null && storedOtp.equals(otp)) {
            // Rimuove l'OTP per garantirne l'uso singolo
            keyCommands.del(email); 
            return true;
        } else {
            return false;
        }
    }
}