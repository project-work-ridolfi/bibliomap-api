package it.unipegaso.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import it.unipegaso.database.model.User;
import jakarta.inject.Inject;

@QuarkusTest
public class UsersRepositoryTest {

    @Inject
    UsersRepository usersRepository;
    
    private final String TEST_EMAIL = "test_user_unique@bibliomap.it";
    private final String TEST_USERNAME = "testuser";

    // Utente di base, creato nel BeforeEach e pulito nell'AfterEach
    private User testUser; 

    // Helper per creare un utente base
    private User createBaseUser() {
        User user = new User();
        user.username = TEST_USERNAME;
        user.email = TEST_EMAIL;
        user.hashedPassword = "hashedpassword";
        user.acceptedTerms = true;
        return user;
    }

    @BeforeEach
    public void setup() {
        // Garantisce che il documento non esista prima del test
        usersRepository.delete(UsersRepository.EMAIL, TEST_EMAIL); 
        testUser = createBaseUser();
    }
    
    @AfterEach
    public void cleanup() {
        // Garantisce che il documento venga rimosso dopo il test
        usersRepository.delete(UsersRepository.EMAIL, TEST_EMAIL); 
    }
    

    @Test
    public void testCreateUser_Success() {
        assertTrue(usersRepository.create(testUser) != null, "La creazione utente deve avere successo.");
        assertNotNull(testUser.createdAt, "La data di creazione deve essere impostata.");
    }

    @Test
    public void testCreateUser_DuplicateEmail() {
        // Crea l'utente la prima volta (dovrebbe funzionare)
        usersRepository.create(testUser); 
        
        // Tenta di creare lo stesso utente una seconda volta
        User duplicateUser = createBaseUser(); 
        
        // eccezione IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            usersRepository.create(duplicateUser);
        }, "Creazione duplicata deve lanciare IllegalArgumentException.");
    }
    
    @Test
    public void testFindByEmail_Found() {
        usersRepository.create(testUser);
        assertTrue(usersRepository.findByEmail(TEST_EMAIL).isPresent(), "L'utente deve essere trovato tramite email.");
    }
    
    @Test
    public void testFindByEmail_NotFound() {
        // L'utente non viene creato, quindi non esiste nel DB
        assertFalse(usersRepository.findByEmail("non_trovato@mail.it").isPresent(), "L'utente non deve essere trovato.");
    }

    @Test
    public void testFindByUsername_EmptyInput() {
        // Input nullo o vuoto (dovrebbe ritornare Optional.empty() senza lanciare eccezioni)
        assertTrue(usersRepository.findByUsername(null).isEmpty(), "Username nullo deve ritornare Optional.empty.");
        assertTrue(usersRepository.findByUsername("").isEmpty(), "Username vuoto deve ritornare Optional.empty.");
    }

    @Test
    public void testUpdateUser_NotFound() {
        // Crea un utente fittizio MA con un ID casuale che non esiste
        User nonExistingUser = createBaseUser();
        nonExistingUser.id = "4b277b75-1e3d-4c3e-a892-a1b9d4e5f6g7"; // UUID casuale
        
        // updateUtente deve ritornare false (0 modificati)
        assertFalse(usersRepository.update(nonExistingUser), "Aggiornamento utente non esistente deve ritornare false.");
    }
}