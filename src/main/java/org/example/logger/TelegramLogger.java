package org.example.logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TelegramLogger {

    private static final String BOT_TOKEN = "7334236180:AAHjmtzEd6p_WFnVLjfv5X6sYOukhqPYASI";
    private static final String CHAT_ID = "-4503197057";

    public static void sendMessage(String message) {
        try {
            String encodedMessage = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + encodedMessage;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        System.err.println("Failed to send Telegram message: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("Error sending Telegram message: " + e.getMessage());
        }
    }
}

