package it.unipegaso.service.otp;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HMACSHA1OTPValidator {

    private static final int DEFAULT_CODE_DIGITS = 6;
    private static final boolean DEFAULT_ADD_CHECKSUM = false;
    private static final int DEFAULT_TRUNCATION_OFFSET = 16;

    private static final int[] doubleDigits = { 0, 2, 4, 6, 8, 1, 3, 5, 7, 9 };
    private static final int[] DIGITS_POWER = { 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };
    private IBaseStringEncoding baseStringEncoding;

    public HMACSHA1OTPValidator() {
        baseStringEncoding = new Base32StringEncoding();
    }

    public HMACSHA1OTPValidator(IBaseStringEncoding aBaseStringEncoding) {
        baseStringEncoding = aBaseStringEncoding;
    }

     // Valida una password OTP HMAC-SHA1 secondo lo standard OATH.
    public boolean validatePassword(String password, String secret, long movingFactor)
            throws InvalidKeyException, NoSuchAlgorithmException, DecodingException {

        return validatePassword(password, secret, movingFactor, DEFAULT_CODE_DIGITS, DEFAULT_ADD_CHECKSUM,
                DEFAULT_TRUNCATION_OFFSET);

    }

     // Genera una password OTP HMAC-SHA1 secondo lo standard OATH.
    public String generatePassword(String secret, long movingFactor)
            throws InvalidKeyException, NoSuchAlgorithmException, DecodingException {

        return generatePassword(secret, movingFactor, DEFAULT_CODE_DIGITS, DEFAULT_ADD_CHECKSUM,
                DEFAULT_TRUNCATION_OFFSET);
    }


     // Valida una password OTP HMAC-SHA1 con parametri personalizzati.
    public boolean validatePassword(String password, String secret, long movingFactor, int codeDigits,
            boolean addChecksum, int truncationOffset)
            throws InvalidKeyException, NoSuchAlgorithmException, DecodingException {

        boolean result = false;
        byte[] secretAsBytesArray = baseStringEncoding.decode(secret);

        String n = generateOTP(secretAsBytesArray,
                movingFactor,
                codeDigits,
                addChecksum,
                truncationOffset);

        result = n.equals(password);
        return result;
    }


     // Genera una password OTP HMAC-SHA1 con parametri personalizzati.
    public String generatePassword(String secret, long movingFactor, int codeDigits, boolean addChecksum,
            int truncationOffset)
            throws InvalidKeyException, NoSuchAlgorithmException, DecodingException {

        byte[] secretAsBytesArray = baseStringEncoding.decode(secret);

        return generateOTP(secretAsBytesArray,
                movingFactor,
                codeDigits,
                addChecksum,
                truncationOffset);

    }

    // Calcola il checksum usando l'algoritmo delle carte di credito.
    private int calcChecksum(long num, int digits) {
        boolean doubleDigit = true;
        int total = 0;
        while (0 < digits--) {
            int digit = (int) (num % 10);
            num /= 10;
            if (doubleDigit) {
                digit = doubleDigits[digit];
            }
            total += digit;
            doubleDigit = !doubleDigit;
        }
        int result = total % 10;
        if (result > 0) {
            result = 10 - result;
        }
        return result;
    }
    
    // Calcola l'HMAC-SHA-1 utilizzando JCE.
    private byte[] hmacSha1(byte[] keyBytes, byte[] text)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmacSha1;
        try {
            hmacSha1 = Mac.getInstance("HmacSHA1");
        } catch (NoSuchAlgorithmException nsae) {
            hmacSha1 = Mac.getInstance("HMAC-SHA-1");
        }
        SecretKeySpec macKey = new SecretKeySpec(keyBytes, "RAW");
        hmacSha1.init(macKey);
        return hmacSha1.doFinal(text);
    }

    
    // Genera un valore OTP per i parametri specificati.
    private String generateOTP(byte[] secret,
            long movingFactor,
            int codeDigits,
            boolean addChecksum,
            int truncationOffset)
            throws NoSuchAlgorithmException, InvalidKeyException {

        StringBuilder result = null;
        int digits = addChecksum ? (codeDigits + 1) : codeDigits;
        byte[] text = new byte[8];
        for (int i = text.length - 1; i >= 0; i--) {
            text[i] = (byte) (movingFactor & 0xff);
            movingFactor >>= 8;
        }
        byte[] hash = hmacSha1(secret, text);
        int offset = hash[hash.length - 1] & 0xf;
        if ((0 <= truncationOffset) &&
                (truncationOffset < (hash.length - 4))) {
            offset = truncationOffset;
        }
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        int otp = binary % DIGITS_POWER[codeDigits];
        if (addChecksum) {
            otp = (otp * 10) + calcChecksum(otp, codeDigits);
        }
        result = new StringBuilder(Integer.toString(otp));
        while (result.length() < digits) {
            result.insert(0, '0');
        }
        return result.toString();
    }
}
