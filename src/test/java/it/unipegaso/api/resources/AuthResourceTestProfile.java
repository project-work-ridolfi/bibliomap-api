package it.unipegaso.api.resources;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class AuthResourceTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.mailer.mock", "true",
            "quarkus.auth.otp.debug-mode", "true"
        );
    }
}
