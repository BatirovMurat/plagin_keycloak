package org.example.eimzo;

import org.example.logger.TelegramLogger;

import java.util.HashMap;
import java.util.Map;

public class EIMZOServerResponse {
    private final int statusCode;
    private final String responseBody;

    public EIMZOServerResponse(String responseBody, int statusCode) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;

        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException("Server returned error: " + statusCode + " - " + responseBody);
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRawResponse() {
        return responseBody;
    }

    /**
     * Примитивный парсер: работает только для простых JSON-объектов {"key":"value", ...}
     */
    public String get(String key) {
        Map<String, String> parsed = parseJsonToMap(responseBody);
        return parsed.get(key);
    }

    public Map<String, String> getJsonAsMap() {
        return parseJsonToMap(responseBody);
    }

    private Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1).trim();
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // делим по запятым вне кавычек

            for (String pair : pairs) {
                String[] keyValue = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
                    String value = keyValue[1].trim().replaceAll("^\"|\"$", "");
                    map.put(key, value);
                }
            }
        }
        return map;
    }
}
