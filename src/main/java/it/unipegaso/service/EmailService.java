package it.unipegaso.service;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
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
	@ConfigProperty(name = "quarkus.email.debug-mode", defaultValue = "false")
	boolean debugEmail;

	@Inject
	@ConfigProperty(name = "quarkus.auth.otp.from", defaultValue = "noreply@bibliomap.it")
	String fromEmail;

	@Inject
	@ConfigProperty(name = "quarkus.email.base-url", defaultValue = "http://localhost:5173/") //TODO
	String baseUrl;

	@Inject
	Mailer mailer;


	public boolean sendLoanRequestEmail(String recipientEmail, String recipientName, String requesterUsername, String bookTitle, String author, String loanId) {

		String loanUrl = baseUrl + "/TODOLOANREQUEST/" + loanId ; //TODO

		LoanEmailData data = new LoanEmailData (recipientName, requesterUsername, bookTitle, author, loanUrl);


		String htmlBody = loanRequestTemplate
				.data(data) 
				.render();

		String subject = "[Bibliomap] Hai una nuova richiesta di prestito!";

		if(debugEmail) {
			LOG.info("DEBUG EMAIL - subject: " + subject);
			LOG.info(htmlBody);
			return true;
		}

		try {
			mailer.send(Mail.withHtml(
					recipientEmail, // to
					subject,       
					htmlBody        
					)
					.setFrom(fromEmail) 
					);

			LOG.infof("Email di richiesta prestito inviata con successo a %s", recipientEmail);
			return true;

		} catch (Exception e) {
			LOG.errorf(e, "ERRORE SMTP durante l'invio di richiesta prestito a %s. Controllare la configurazione.", recipientEmail);
			return false;
		}
	}


	/**
	 * Invia un'email all'utente con il codice OTP.
	 * @param recipientEmail L'email del destinatario.
	 * @param otpCode Il codice OTP generato.
	 * @param recipientName L'username del destinatario.
	 * @param verificationUrl TODO l'url in caso abbiano chiuso la pagina per la verifica dell'otp
	 * @return true se l'email è stata processata (mock o inviata), false in caso di errore SMTP.
	 */
	public boolean sendOtpEmail(String recipientEmail, String otpCode, String recipientName) {

		// crea l'oggetto dati per il template
		String verificationUrl = baseUrl + "/TODOOTP"; //TODO

		OtpEmailData data = new OtpEmailData(recipientName, otpCode, verificationUrl);

		// genera il contenuto HTML usando Qute
		// .data() passa l'oggetto OtpEmailData al template
		String htmlBody = otpEmailTemplate
				.data(data) 
				.render();

		String subject = "[Bibliomap] Il tuo codice di verifica OTP: " + otpCode;

		try {
			mailer.send(Mail.withHtml(
					recipientEmail, // to
					subject,       
					htmlBody        
					)
					.setFrom(fromEmail) 
					);

			LOG.infof("Email di verifica OTP HTML inviata con successo a %s", recipientEmail);
			return true;

		} catch (Exception e) {
			LOG.errorf(e, "ERRORE SMTP durante l'invio OTP HTML a %s. Controllare la configurazione.", recipientEmail);
			return false;
		}
	}

	public class OtpEmailData {
		private final String recipientName;
		private final String otpCode;
		private final String verificationUrl;

		public OtpEmailData(String recipientName, String otpCode, String verificationUrl) {
			this.recipientName = recipientName;
			this.otpCode = otpCode;
			this.verificationUrl = verificationUrl;
		}

		public String getRecipientName() { return recipientName; }
		public String getOtpCode() { return otpCode; }
		public String getVerificationUrl() { return verificationUrl; }
	}

	public class LoanEmailData {
		private final String recipientName;
		private final String requesterName;
		private final String bookTitle;
		private final String author;
		private final String loanUrl;

		public LoanEmailData(String recipientName, String requesterName, String bookTitle, String author, String loanUrl) {
			this.recipientName = recipientName;
			this.requesterName = requesterName;
			this.bookTitle = bookTitle;
			this.author = author;
			this.loanUrl = loanUrl;
		}

		public String getRecipientName() { return recipientName; }
		public String getRequesterName() { return requesterName; }
		public String getBookTitle() { return bookTitle; }
		public String getAuthor() { return author; }
		public String getLoanUrl() { return loanUrl; }

	}
}