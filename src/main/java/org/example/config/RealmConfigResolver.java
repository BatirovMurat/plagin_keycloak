package org.example.config;

import org.keycloak.models.KeycloakSession;
import java.util.Map;

public class RealmConfigResolver {

    public static Map<String, String> resolvePayload(KeycloakSession session) {
        String realmName = session.getContext().getRealm().getName(); // multibank_dev или multibank_prod

        String suffix;
        if (realmName.endsWith("_dev")) {
            suffix = "DEV";
        } else if (realmName.endsWith("_prod")) {
            suffix = "PROD";
        } else {
            throw new IllegalStateException("Unknown realm: " + realmName);
        }

        // Берём из ENV или подставляем дефолтные значения
        String appId = getenvOrDefault("MULTICARD_APP_ID_" + suffix,
                suffix.equals("DEV") ? "dev_ox" : "my_prod_app_id");

        String secret = getenvOrDefault("MULTICARD_APP_SECRET_" + suffix,
                suffix.equals("DEV") ? "J8Q6G7cFleyS" : "my_prod_secret");

        String baseUrl = getenvOrDefault("MULTICARD_BASE_URL_" + suffix,
                suffix.equals("DEV") ? "https://dev-mesh.multicard.uz" : "https://mesh.multicard.uz");

        return Map.of(
                "multicard_application_id", appId,
                "multicard_secret", secret,
                "multicard_base_url", baseUrl
        );
    }

    private static String getenvOrDefault(String key, String def) {
        String val = System.getenv(key);
        return (val == null || val.isBlank()) ? def : val;
    }
}
