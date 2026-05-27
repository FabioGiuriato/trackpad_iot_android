package com.example.trackpad_iot_android;

import android.content.Context;
import android.content.SharedPreferences;

class SessionStore {
    private static final String PREFS_NAME = "trackpad_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_BASE_URL = "base_url";

    private final SharedPreferences preferences;

    SessionStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    String getToken() {
        return preferences.getString(KEY_TOKEN, "");
    }

    String getUsername() {
        return preferences.getString(KEY_USERNAME, "");
    }

    String getBaseUrl() {
        return preferences.getString(KEY_BASE_URL, ApiClient.DEFAULT_BASE_URL);
    }

    void saveBaseUrl(String baseUrl) {
        preferences.edit()
                .putString(KEY_BASE_URL, baseUrl)
                .apply();
    }

    void saveSession(String token, String username, String baseUrl) {
        preferences.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USERNAME, username)
                .putString(KEY_BASE_URL, baseUrl)
                .apply();
    }

    void clearSession() {
        preferences.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .apply();
    }

    boolean hasToken() {
        return !getToken().isEmpty();
    }
}
