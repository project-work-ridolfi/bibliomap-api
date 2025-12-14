package it.unipegaso.scheduler;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import it.unipegaso.database.LoansRepository;
import it.unipegaso.database.UsersRepository;
import it.unipegaso.database.model.Loan;
import it.unipegaso.database.model.User;
import it.unipegaso.service.EmailService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LoanReminderScheduler {

	private static final Logger LOG = Logger.getLogger(LoanReminderScheduler.class);

	@Inject
	LoansRepository loansRepository;

	@Inject
	UsersRepository userRepository;

	@Inject
	EmailService emailService;

	@Scheduled(cron = "0 0 9 * * ?")
	void checkOverdueLoans() {
		LOG.info("esecuzione scheduler solleciti prestiti");

		Date now = new Date();

		// recupero prestiti in corso scaduti
		List<Loan> overdueLoans = loansRepository.findOverdue(now);

		for (Loan loan : overdueLoans) {
			try {
				Optional<User> opRequester = userRepository.get(loan.getRequesterId());
				
				if (opRequester.isPresent()) {
					User requester = opRequester.get();
					
					// invio email sollecito
					boolean success = emailService.sendOverdueReminderEmail(
							requester.getEmail(), 
							requester.getUsername(), 
							loan.getTitle(), 
							loan.getExpectedReturnDate()
					);

					if (success) {
						LOG.infof("sollecito inviato a %s per il libro %s", requester.getEmail(), loan.getTitle());
					}
				}
			} catch (Exception e) {
				LOG.errorf("errore invio sollecito per prestito %s: %s", loan.getId(), e.getMessage());
			}
		}
	}
}