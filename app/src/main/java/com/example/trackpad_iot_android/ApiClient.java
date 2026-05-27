package com.example.trackpad_iot_android;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

class ApiClient {
    static final String DEFAULT_BASE_URL = "http://10.230.52.56:8000/api/v1";
    static final String EMULATOR_BASE_URL = "http://10.0.2.2:8000/api/v1";

    private String baseUrl;

    ApiClient(String baseUrl) {
        setBaseUrl(baseUrl);
    }

    void setBaseUrl(String baseUrl) {
        String nextBaseUrl = baseUrl == null || baseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL
                : baseUrl.trim();

        while (nextBaseUrl.endsWith("/")) {
            nextBaseUrl = nextBaseUrl.substring(0, nextBaseUrl.length() - 1);
        }

        this.baseUrl = nextBaseUrl;
    }

    String getBaseUrl() {
        return baseUrl;
    }

    ApiResponse get(String path, String token) throws IOException {
        return get(path, token, 8000);
    }

    ApiResponse get(String path, String token, int timeoutMs) throws IOException {
        return request("GET", path, null, token, timeoutMs);
    }

    ApiResponse post(String path, JSONObject body, String token) throws IOException {
        return request("POST", path, body, token, 8000);
    }

    ApiResponse put(String path, JSONObject body, String token) throws IOException {
        return request("PUT", path, body, token, 8000);
    }

    private ApiResponse request(String method, String path, JSONObject body, String token, int timeoutMs) throws IOException {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Cache-Control", "no-cache");

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                connection.setDoOutput(true);
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(body.toString());
                }
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String rawBody = readStream(stream);

            return new ApiResponse(statusCode, rawBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }

        return builder.toString();
    }

    static class ApiResponse {
        final int statusCode;
        final String rawBody;
        final JSONObject json;

        ApiResponse(int statusCode, String rawBody) {
            this.statusCode = statusCode;
            this.rawBody = rawBody == null ? "" : rawBody;
            this.json = parseJson(this.rawBody);
        }

        boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }

        String message() {
            if (json != null) {
                String message = json.optString("message", "");

                if (!message.isEmpty()) {
                    return message;
                }
            }

            return rawBody.isEmpty() ? "Errore HTTP " + statusCode : rawBody;
        }

        private static JSONObject parseJson(String rawBody) {
            try {
                return rawBody == null || rawBody.isEmpty() ? new JSONObject() : new JSONObject(rawBody);
            } catch (JSONException exception) {
                return new JSONObject();
            }
        }
    }
}
