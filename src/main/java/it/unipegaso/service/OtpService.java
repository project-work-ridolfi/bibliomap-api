package it.unipegaso.service;


import java.time.Instant;
import java.util.Random;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import it.unipegaso.database.model.OtpCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OtpService {

    private static final Logger LOG = Logger.getLogger(OtpService.class);
    private static final Random RANDOM = new Random();
    private static final int CODE_LENGTH = 6;

    @Inject
    EmailService emailService;

    @Inject
    @ConfigProperty(name = "quarkus.auth.otp.duration-minutes", defaultValue = "5")
    long otpDurationMinutes;

    /**
     * Genera, salva e invia un nuovo codice OTP.
     * @param recipientEmail L'email del destinatario e chiave di storage.
     * @return true se l'invio è stato processato con successo (anche in modalità mock).
     */
    public boolean sendOtp(String recipientEmail, String otp) {
        String otpCode = generateRandomOtpCode(CODE_LENGTH);
        
        // Salva il codice (MongoDB)
        // Rimuove eventuali codici pendenti per lo stesso utente
        OtpCode.delete("email", recipientEmail); 
        
        OtpCode newOtp = OtpCode.create(recipientEmail, otpCode, otpDurationMinutes);
        newOtp.persist();
        
        LOG.infof("OTP generato e salvato per %s. Scadenza: %s", recipientEmail, newOtp.expirationTime);

        //Invia il codice
        return emailService.sendOtpEmail(recipientEmail, otpCode);
    }

    /**
     * Verifica il codice OTP fornito dall'utente.
     * @param email L'email dell'utente.
     * @param providedCode Il codice fornito dall'utente.
     * @return true se il codice è valido e non scaduto, false altrimenti.
     */
    public boolean verifyOtp(String email, String providedCode) {
        OtpCode storedOtp = OtpCode.find("email", email).firstResult();

        if (storedOtp == null) {
            LOG.warnf("Tentativo di verifica OTP fallito: Nessun codice trovato per %s.", email);
            return false; 
        }
        
        // Verifica scadenza
        if (Instant.now().isAfter(storedOtp.expirationTime)) {
            LOG.warnf("Tentativo di verifica OTP fallito: Codice scaduto per %s.", email);
            storedOtp.delete(); 
            return false;
        }

        // Verifica corrispondenza
        if (!storedOtp.code.equals(providedCode)) {
            LOG.warnf("Tentativo di verifica OTP fallito: Codice errato per %s.", email);
            return false; 
        }
        
        // Successo: elimina il codice usato
        storedOtp.delete();
        LOG.infof("Verifica OTP riuscita per %s. Codice eliminato.", email);
        return true;
    }

    /**
     * Genera un codice numerico casuale della lunghezza specificata.
     */
    public String generateRandomOtpCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
