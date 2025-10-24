package org.example.sms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.logger.TelegramLogger;

public class SmsService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String token;
    private final String baseUrl;
    private final String prefix = "/sms";

    private final String appId;
    private final String secret;

    public SmsService(Map<String, String> creds) {
        this.appId = creds.get("multicard_application_id");
        this.secret = creds.get("multicard_secret");
        this.baseUrl = creds.get("multicard_base_url");

        this.token = authenticate().orElseThrow(() -> new RuntimeException("Unable to authenticate"));
    }

    public Optional<String> authenticate() {
        HttpClient client = HttpClient.newHttpClient();

        Map<String, String> payload = Map.of(
                "application_id", appId, //java.lang.NullPointerException
                "secret", secret
        );

        TelegramLogger.sendMessage("************* authenticate sms service ****************");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


            TelegramLogger.sendMessage("************* sms service response ****************");
            TelegramLogger.sendMessage(response.body());

            JsonNode json = mapper.readTree(response.body());
            if (json.has("token")) {
                return Optional.of(json.get("token").asText());
            } else {
                System.err.println("Auth failed: " + response.body());
                return Optional.empty();
            }

        } catch (Exception e) {
            e.printStackTrace();
            TelegramLogger.sendMessage(e.getMessage());
            return Optional.empty();
        }
    }

    public String sendSms(Map<String, Object> data) throws Exception {
        URI uri = URI.create(baseUrl + prefix + "/?hide_otp=1");

        TelegramLogger.sendMessage("************** sendSms ****************");

        String body = mapper.writeValueAsString(data);

        TelegramLogger.sendMessage(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        TelegramLogger.sendMessage(uri.getQuery());

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        TelegramLogger.sendMessage(response.body());
        return response.body(); // либо возвращай объект
    }

    public boolean checkOtp(Map<String, Object> data) throws Exception {
        String smsId = data.get("sms_id").toString();
        URI uri = URI.create(baseUrl + prefix + "/" + smsId);


        System.out.println("************** checkOtp ****************");
        System.out.println(data);

        Map<String, Object> otpBody = Map.of("otp", data.get("otp"));
        String body = mapper.writeValueAsString(otpBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response);
        JsonNode json = mapper.readTree(response.body());

        if (json.has("success") && json.get("success").asBoolean()) {
            return true;
        }

        // Ошибка
        String errorCode = json.path("error").path("code").asText("UNKNOWN_ERROR");
        String details = json.path("error").path("details").asText("Неизвестная ошибка");

        throw new IllegalArgumentException("OTP check failed: " + errorCode + " — " + details);
    }
}

