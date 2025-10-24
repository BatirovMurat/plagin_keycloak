package org.example.eimzo;

import org.example.logger.TelegramLogger;

import java.io.IOException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

public class EIMZOServerService {

    private final String baseUrl;

    public EIMZOServerService() {
        this.baseUrl = "http://10.96.237.246:8080";
    }

    public boolean pkcs7VerifyAttached(String pkcs7) throws IOException, InterruptedException {
        String targetUrl = baseUrl + "/backend/pkcs7/verify/attached";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(pkcs7, StandardCharsets.UTF_8))
                .build();

        TelegramLogger.sendMessage(Arrays.toString(pkcs7.getBytes(StandardCharsets.UTF_8)));
        TelegramLogger.sendMessage("Sending request to: " + request.uri());

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        String responseBody = response.body();

        String status = extractJsonValue(responseBody, "status");

        TelegramLogger.sendMessage("Response code: " + response.statusCode());
        TelegramLogger.sendMessage("status code: " + status);

        return status.equals("1");
    }

    private String extractJsonValue(String json, String key) {
        json = json.trim();
        key = "\"" + key + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;

        // Отрезаем от двоеточия до запятой или конца строки
        int valueStart = colonIndex + 1;

        // Пропускаем пробелы
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        char firstChar = json.charAt(valueStart);
        int valueEnd;

        if (firstChar == '"') {
            // Значение — строка
            valueStart++;
            valueEnd = json.indexOf('"', valueStart);
            return json.substring(valueStart, valueEnd);
        } else {
            // Значение — число, boolean или null
            valueEnd = json.indexOf(',', valueStart);
            if (valueEnd == -1) {
                valueEnd = json.indexOf('}', valueStart);
            }
            if (valueEnd == -1) return null;

            return json.substring(valueStart, valueEnd).trim();
        }
    }
}

