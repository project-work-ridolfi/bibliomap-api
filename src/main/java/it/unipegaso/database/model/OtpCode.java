package it.unipegaso.database.model;

import java.time.Instant;

import org.bson.codecs.pojo.annotations.BsonProperty;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

@MongoEntity(collection = "otp_codes")
public class OtpCode extends PanacheMongoEntity {

    @BsonProperty("email")
    public String email;

    // Il codice OTP generato
    @BsonProperty("code")
    public String code;

    // Timestamp di creazione
    @BsonProperty("creationTime")
    public Instant creationTime;

    // Timestamp di scadenza 
    @BsonProperty("expirationTime")
    public Instant expirationTime;

    public static OtpCode create(String email, String code, long durationMinutes) {
        OtpCode otp = new OtpCode();
        otp.email = email;
        otp.code = code;
        otp.creationTime = Instant.now();
        otp.expirationTime = otp.creationTime.plusSeconds(durationMinutes * 60);
        return otp;
    }
}
