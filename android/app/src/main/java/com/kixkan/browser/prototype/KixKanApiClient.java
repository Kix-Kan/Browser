package com.kixkan.browser.prototype;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.io.OutputStream;

final class KixKanApiClient {
    static final class ApiException extends Exception {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    static final class LoginResult {
        final String token;
        final String email;
        final String displayName;
        final long expiresAt;
        LoginResult(String token, String email, String displayName, long expiresAt) {
            this.token = token;
            this.email = email;
            this.displayName = displayName;
            this.expiresAt = expiresAt;
        }
    }

    private KixKanApiClient() {}

    static LoginResult login(String email, String password) throws Exception {
        JSONObject response = request("POST", "/api/v1/accounts/login", null, new JSONObject().put("email", email).put("password", password));
        JSONObject account = response.getJSONObject("account");
        return new LoginResult(response.getString("token"), account.getString("email"), account.optString("displayName"), response.optLong("expiresAtEpochMs", AccountSession.defaultExpiration()));
    }

    static void logout(String token) throws Exception {
        request("POST", "/api/v1/accounts/logout", token, new JSONObject());
    }

    static boolean emailVerified(String token) throws Exception {
        JSONObject response = request("GET", "/api/v1/accounts/me", token, null);
        return response.getJSONObject("account").optBoolean("emailVerified", false);
    }

    static JSONObject pushBookmarks(String token, List<BookmarkStore.Entry> entries) throws Exception {
        JSONArray changes = new JSONArray();
        for (BookmarkStore.Entry entry : entries) {
            JSONObject change = new JSONObject().put("id", entry.id).put("deleted", entry.deleted);
            if (!entry.deleted) change.put("title", entry.title).put("url", entry.url).put("folder", entry.folder).put("position", entry.position);
            changes.put(change);
        }
        return request("PUT", "/api/v1/browser/bookmarks", token, new JSONObject().put("changes", changes));
    }

    static JSONObject pullBookmarks(String token, long cursor) throws Exception {
        return request("GET", "/api/v1/browser/bookmarks?after=" + cursor + "&limit=200", token, null);
    }

    private static JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        URL url = new URL(BuildConfig.KIXKAN_API_BASE_URL + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalStateException("KixKan account API must use HTTPS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(encoded.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) { output.write(encoded); }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = stream == null ? "" : readLimited(stream, 512_000);
        connection.disconnect();
        JSONObject parsed = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (status < 200 || status >= 300) throw new ApiException(status, parsed.optString("message", "KixKan account request failed."));
        return parsed;
    }

    private static String readLimited(InputStream stream, int maximum) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximum) throw new IllegalStateException("KixKan API response is too large");
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
