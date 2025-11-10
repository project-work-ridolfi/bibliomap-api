package it.unipegaso.service;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject; 

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @Inject
    @ConfigProperty(name = "quarkus.auth.otp.mock-enabled", defaultValue = "true")
    boolean mockOtpEnabled;

    @Inject
    @ConfigProperty(name = "quarkus.auth.otp.from", defaultValue = "noreply@bibliomap.it")
    String fromEmail;

    @Inject
    Mailer mailer;

    /**
     * Invia un'email all'utente con il codice OTP.
     * @param recipientEmail L'email del destinatario.
     * @param otpCode Il codice OTP generato.
     * @return true se l'email è stata processata (mock o inviata), false in caso di errore SMTP.
     */
    public boolean sendOtpEmail(String recipientEmail, String otpCode) {

        try {
            mailer.send(Mail.withText(
                    recipientEmail, // to
                    "Il tuo codice di verifica Bibliomap", // subject
                    "Ciao,\n\nIl tuo codice di verifica Bibliomap è: " + otpCode + 
                      "\n\nInserisci questo codice per completare la tua registrazione." // text
                )
                .setFrom(fromEmail) 
            );
            
            LOG.infof("Email di verifica OTP inviata con successo a %s", recipientEmail);
            return true;
            
        } catch (Exception e) {
            LOG.errorf(e, "ERRORE SMTP durante l'invio OTP a %s. Controllare la configurazione.", recipientEmail);
            return false;
        }
    }
}