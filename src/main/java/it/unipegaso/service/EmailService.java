package it.unipegaso.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import it.unipegaso.service.client.BrevoClient;
import it.unipegaso.api.dto.BrevoRequest;
import it.unipegaso.api.dto.BrevoContact;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Base64;
import it.unipegaso.api.dto.BrevoAttachment; 

@ApplicationScoped
public class EmailService {

	private static final Logger LOG = Logger.getLogger(EmailService.class);

	@Inject
	@Location("EmailService/otpEmail.html")
	Template otpEmailTemplate;

	@Inject
	@Location("EmailService/loanRequest.html")
	Template loanRequestTemplate;

	@Inject
	@Location("EmailService/requestResponse.html")
	Template requestResponseTemplate;
	
	@Inject
	@Location("EmailService/accountDeleted.html")
	Template accountDeletedTemplate;
	
	@Inject
	@Location("EmailService/accountDeletionBlocked.html")
	Template accountDeletionBlockedTemplate;
	
	@Inject
	@Location("EmailService/loanStarted.html")
	Template loanStartedTemplate;

	@Inject
	@Location("EmailService/returnConfirmation.html")
	Template returnConfirmationTemplate;

	@Inject
	@Location("EmailService/overdueReminder.html")
	Template overdueReminderTemplate;

	
	@Inject
	@Location("EmailService/conctatUser.html")
	Template conctactUserTemplate;
	
	@Inject
	@ConfigProperty(name = "email.debug-mode", defaultValue = "false")
	boolean debugEmail;

	@Inject
	@ConfigProperty(name = "auth.otp.duration-minutes", defaultValue = "5")
	long otpDurationMinutes;

	@Inject
	@ConfigProperty(name = "email.base-url", defaultValue = "https://bibliomap-ui.onrender.com/")
	String baseUrl;

	@Inject
	@ConfigProperty(name = "brevo.api.key")
	String brevoApiKey;

	@Inject
	@RestClient
	BrevoClient brevoClient;

	// Invia notifica di esito (accettazione/rifiuto) al richiedente.
	public boolean sendRequestResponseEmail(String recipientEmail, String recipientName, String bookTitle, String action, String ownerNotes, String days, String slots) {

		boolean isAccepted = "accepted".equalsIgnoreCase(action) || "accept".equalsIgnoreCase(action) || "true".equalsIgnoreCase(action);

		Map<String, Object> data = new HashMap<>();
		data.put("recipientName", recipientName);
		data.put("bookTitle", bookTitle);
		data.put("isAccepted", isAccepted);
		data.put("ownerNotes", ownerNotes); 
		data.put("days", days);   // nuovo
	    data.put("slots", slots); // nuovo
		// Link alla dashboard per vedere i dettagli
		data.put("dashboardUrl", baseUrl + "dashboard");

		String htmlBody = requestResponseTemplate
				.data(data) 
				.render();

		String statusText = isAccepted ? "ACCETTATA" : "RIFIUTATA";
		String subject = "La tua richiesta di prestito è stata " + statusText;

		return sendEmail(recipientEmail, subject, htmlBody, "Esito richiesta");
	}

	// Invia notifica di nuova richiesta di prestito al proprietario del libro.
	public boolean sendLoanRequestEmail(String recipientEmail, String recipientName, String requesterName, String bookTitle, String author, String loanId) {

		// url per portare l'utente direttamente alla gestione della richiesta
		String loanUrl = baseUrl + "dashboard?tab=requests&highlight=" + loanId;

		Map<String, Object> data = new HashMap<>();
		data.put("recipientName", recipientName);
		data.put("requesterName", requesterName); 
		data.put("bookTitle", bookTitle);
		data.put("author", author);
		data.put("loanUrl", loanUrl);

		String htmlBody = loanRequestTemplate
				.data(data)
				.render();

		String subject = "Hai una nuova richiesta di prestito!";

		return sendEmail(recipientEmail, subject, htmlBody, "Nuova richiesta prestito");
	}

	// invia conferma di fine prestito al richiedente
	public boolean sendReturnConfirmationEmail(String recipientEmail, String recipientName, String bookTitle) {

		Map<String, Object> data = new HashMap<>();
		data.put("recipientName", recipientName);
		data.put("bookTitle", bookTitle);
		data.put("dashboardUrl", baseUrl + "dashboard");

		String htmlBody = returnConfirmationTemplate
				.data(data)
				.render();

		String subject = "Il prestito di \"" + bookTitle + "\" è concluso";

		return sendEmail(recipientEmail, subject, htmlBody, "Conferma restituzione");
	}

	// invia sollecito per prestito scaduto
	public boolean sendOverdueReminderEmail(String recipientEmail, String recipientName, String bookTitle, Date dueDate) {

		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

		Map<String, Object> data = new HashMap<>();
		data.put("recipientName", recipientName);
		data.put("bookTitle", bookTitle);
		data.put("dueDate", sdf.format(dueDate));
		data.put("dashboardUrl", baseUrl + "dashboard");

		String htmlBody = overdueReminderTemplate
				.data(data)
				.render();

		String subject = "Sollecito: il periodo di prestito è scaduto";

		return sendEmail(recipientEmail, subject, htmlBody, "Sollecito scadenza");
	}

	// Invia un'email all'utente con il codice OTP per la verifica.
	public boolean sendOtpEmail(String recipientEmail, String otpCode, String recipientName, boolean hasForgottenPassword) {
	    String verificationUrl = baseUrl + "signup?email=" +  recipientEmail; 

	    Map<String, Object> data = new HashMap<>();
	    data.put("recipientName", recipientName);
	    data.put("otpCode", otpCode);
	    data.put("verificationUrl", verificationUrl);
	    data.put("isReset", hasForgottenPassword);
	    data.put("otpDurationMinutes", otpDurationMinutes); 

	    String htmlBody = otpEmailTemplate
	            .data(data)
	            .render();

	    String subject = hasForgottenPassword 
	        ? "[Bibliomap] Recupero password - Codice OTP: " + otpCode
	        : "[Bibliomap] Benvenuto! Verifica il tuo account - Codice OTP: " + otpCode;

	    return sendEmail(recipientEmail, subject, htmlBody, "Codice OTP");
	}
	
	// invia notifica di conferma eliminazione account
	public boolean sendAccountDeletedEmail(String recipientEmail, String recipientName) {
	    Map<String, Object> data = new HashMap<>();
	    data.put("recipientName", recipientName);

	    String htmlBody = accountDeletedTemplate
	            .data(data)
	            .render();

	    String subject = "Il tuo account Bibliomap è stato eliminato";

	    return sendEmail(recipientEmail, subject, htmlBody, "Eliminazione account");
	}
	
	public boolean sendDeletionBlockedEmail(String recipientEmail, String recipientName, List<Loan> blockingLoans) {
	    Map<String, Object> data = new HashMap<>();
	    data.put("recipientName", recipientName);
	    data.put("loans", blockingLoans); // Passiamo la lista al template

	    String htmlBody = accountDeletionBlockedTemplate
	            .data(data)
	            .render();

	    String subject = "Azione richiesta: non puoi ancora eliminare il tuo account Bibliomap";

	    return sendEmail(recipientEmail, subject, htmlBody, "Eliminazione bloccata");
	}
	
	public void sendLoanStartedEmail(Loan loan, User owner, User requester) {

		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
	    String formattedReturnDate = loan.getExpectedReturnDate() != null ? sdf.format(loan.getExpectedReturnDate()) : "N/D";

	    // Email per il richiedente
	    Map<String, Object> reqData = new HashMap<>();
	    reqData.put("recipientName", requester.getUsername());
	    reqData.put("bookTitle", loan.getTitle());
	    reqData.put("partnerName", owner.getUsername());
	    reqData.put("returnDate", formattedReturnDate);
	    reqData.put("isOwner", false);
	    
	    String htmlBody = loanStartedTemplate
	            .data(reqData)
	            .render();
	    
	    sendEmail(requester.getEmail(), "Prestito iniziato: " + loan.getTitle(), htmlBody, "Inizio prestito per richiedente");

	    // Email per il proprietario
	    Map<String, Object> ownerData = new HashMap<>();
	    ownerData.put("recipientName", owner.getUsername());
	    ownerData.put("bookTitle", loan.getTitle());
	    ownerData.put("partnerName", requester.getUsername());
	    ownerData.put("returnDate", formattedReturnDate); 
	    ownerData.put("isOwner", true);
	    
	    htmlBody = loanStartedTemplate
	            .data(ownerData)
	            .render();
	   
	    sendEmail(owner.getEmail(), "Conferma consegna: " + loan.getTitle(), htmlBody, "Inizio prestito per proprietario");
	}
	
	public void sendContactRequestEmail(String email, String toUsername, String fromUsername, String title,
			Map<String, String> request) {

		
		String notes = request.getOrDefault("notes", "");
		String selectedDays = request.getOrDefault("days", "");  
		String selectedSlots = request.getOrDefault("slots", ""); 
		
	    Map<String, Object> data = new HashMap<>();
	    data.put("recipientName", fromUsername);
	    data.put("ownerName", toUsername);
	    data.put("title", title); 
	    data.put("notes", notes);
	    data.put("selectedDays", selectedDays); 
	    data.put("selectedSlots", selectedSlots);
	    
	    String subject = request.getOrDefault("subject", "Richiesta di contatto da " + fromUsername);
	    
	    String htmlBody = conctactUserTemplate
	            .data(data)
	            .render();
	    
	    sendEmail(email, subject , htmlBody, "Richiesta di contatto");

		
	}
	
	public boolean sendExportEmail(String recipientEmail, String recipientName, byte[] pdfBytes) {
    String htmlBody = "<p>Ciao " + recipientName + ",</p><p>In allegato trovi il documento PDF con tutti i tuoi dati registrati su Bibliomap.</p>";
    String subject = "Export Dati Bibliomap";

    if (debugEmail) {
        LOG.info("--------------------------------------------------");
        LOG.infof("DEBUG EMAIL [Export Dati] to: %s", recipientEmail);
        LOG.info("Body: " + htmlBody);
        LOG.infof("Allegato: bibliomap_export.pdf (%d bytes)", pdfBytes.length);
        LOG.info("--------------------------------------------------");
        return true;
    }

    try {
        // conversione pdf in base64 per api brevo
        String base64Content = Base64.getEncoder().encodeToString(pdfBytes);
        
        BrevoContact sender = new BrevoContact("Bibliomap", "adrianaridolfi91@gmail.com");
        BrevoContact recipient = new BrevoContact(recipientEmail, recipientEmail);
        BrevoAttachment attachment = new BrevoAttachment(base64Content, "bibliomap_export.pdf");
        
        LOG.infof("PDF size bytes: %d", pdfBytes.length);

        BrevoRequest request = new BrevoRequest(
            sender, 
            List.of(recipient), 
            subject, 
            htmlBody, 
            List.of(attachment)
        );

        brevoClient.sendEmail(brevoApiKey, request);
        LOG.infof("email [Export Dati] inviata con successo a %s", recipientEmail);
        return true;
    } catch (Exception e) {
        LOG.errorf(e, "errore api invio [Export Dati] a %s", recipientEmail);
        return false;
    }
}

	private boolean sendEmail(String to, String subject, String body, String logType) {
		if (debugEmail) {
			LOG.info("--------------------------------------------------");
			LOG.infof("DEBUG EMAIL [%s] to: %s", logType, to);
			LOG.infof("Subject: %s", subject);
			LOG.info("Body:");
			LOG.info(body);
			LOG.info("--------------------------------------------------");
			return true;
		}
	
		try {
	        // mittente autorizzato tramite verifica individuale
	        BrevoContact sender = new BrevoContact("Bibliomap", "adrianaridolfi91@gmail.com");
	        BrevoContact recipient = new BrevoContact(null, to);
	        BrevoRequest request = new BrevoRequest(sender, List.of(recipient), subject, body, null);
	
	        // invio tramite porta 443 (standard http)
	        brevoClient.sendEmail(brevoApiKey, request);
	        LOG.infof("email [%s] inviata via api a %s", logType, to);
	        return true;
	    } catch (Exception e) {
	        LOG.errorf(e, "errore invio brevo per %s", to);
	        return false;
	    }
	}


}
