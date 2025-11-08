package it.unipegaso.service.otp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CalculatedSecretKeyStrategy {
    public String load(String seed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes());
            return new Base32StringEncoding().encode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate secret key", e);
        }
    }
}
