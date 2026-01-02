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

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
	@ConfigProperty(name = "quarkus.email.debug-mode", defaultValue = "false")
	boolean debugEmail;


	@Inject
	@ConfigProperty(name = "quarkus.email.base-url", defaultValue = "http://localhost:5173/")
	String baseUrl;

	@Inject
	Mailer mailer;

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
	public boolean sendOtpEmail(String recipientEmail, String otpCode, String recipientName) {

		String verificationUrl = baseUrl + "verify-otp?email=" + recipientEmail; // TODO

		Map<String, Object> data = new HashMap<>();
		data.put("recipientName", recipientName);
		data.put("otpCode", otpCode);
		data.put("verificationUrl", verificationUrl);

		String htmlBody = otpEmailTemplate
				.data(data)
				.render();

		String subject = "[Bibliomap] Il tuo codice di verifica OTP: " + otpCode;

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
	    // Email per il richiedente
	    Map<String, Object> reqData = new HashMap<>();
	    reqData.put("recipientName", requester.getUsername());
	    reqData.put("bookTitle", loan.getTitle());
	    reqData.put("partnerName", owner.getUsername());
	    reqData.put("returnDate", loan.getExpectedReturnDate());
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
	    ownerData.put("returnDate", loan.getExpectedReturnDate());
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
	        try {
	            Path path = Paths.get("target", "export_" + recipientName + "_" + System.currentTimeMillis() + ".pdf");
	            
	            Files.write(path, pdfBytes);
	            
	            LOG.infof("FILE DI DEBUG CREATO: %s", path.toAbsolutePath().toString());
	        } catch (java.io.IOException e) {
	            LOG.error("Errore durante la creazione del file PDF di debug", e);
	        }	        LOG.info("--------------------------------------------------");
	        return true;
	    }

	    try {
	        mailer.send(Mail.withHtml(recipientEmail, subject, htmlBody)
	                .setFrom("Bibliomap <adriana.ridolfi@studenti.unipegaso.it>")
	                .addAttachment("bibliomap_export.pdf", pdfBytes, "application/pdf"));
	        LOG.infof("Email [Export Dati] inviata con successo a %s", recipientEmail);
	        return true;
	    } catch (Exception e) {
	        LOG.errorf(e, "ERRORE SMTP invio [Export Dati] a %s", recipientEmail);
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
			mailer.send(Mail.withHtml(to, subject, body)
					.setFrom("Bibliomap <adriana.ridolfi@studenti.unipegaso.it>")
					.setReplyTo("noreply@invalid.local"));
			LOG.infof("Email [%s] inviata con successo a %s", logType, to);
			return true;
		} catch (Exception e) {
			LOG.errorf(e, "ERRORE SMTP invio [%s] a %s. Controllare configurazione.", logType, to);
			return false;
		}
	}


}