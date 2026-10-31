package com.kixkan.browser.prototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class AccountSession {
    private static final String KEY_ALIAS = "kixkan_browser_account_v1";
    private static final String PREFERENCES = "kixkan_account";
    private static final String SESSION = "encrypted_session";

    static final class Value {
        final String token;
        final String email;
        final String displayName;
        final long expiresAt;

        Value(String token, String email, String displayName, long expiresAt) {
            this.token = token;
            this.email = email;
            this.displayName = displayName;
            this.expiresAt = expiresAt;
        }
    }

    private AccountSession() {}

    static void save(Context context, String token, String email, String displayName, long expiresAt) throws Exception {
        JSONObject json = new JSONObject()
                .put("token", token)
                .put("email", email)
                .put("displayName", displayName)
                .put("expiresAt", expiresAt);
        preferences(context).edit().putString(SESSION, encrypt(json.toString())).apply();
    }

    static Value load(Context context) {
        String encrypted = preferences(context).getString(SESSION, "");
        if (encrypted.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(decrypt(encrypted));
            Value value = new Value(json.getString("token"), json.getString("email"), json.optString("displayName"), json.getLong("expiresAt"));
            if (value.expiresAt <= System.currentTimeMillis()) {
                clear(context);
                return null;
            }
            return value;
        } catch (Exception invalidOrKeyChanged) {
            clear(context);
            return null;
        }
    }

    static long defaultExpiration() {
        return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(29);
    }

    static void clear(Context context) {
        preferences(context).edit().remove(SESSION).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteBuffer value = ByteBuffer.allocate(1 + iv.length + ciphertext.length).put((byte) iv.length).put(iv).put(ciphertext);
        return Base64.encodeToString(value.array(), Base64.NO_WRAP);
    }

    private static String decrypt(String encoded) throws Exception {
        ByteBuffer value = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
        int ivLength = value.get() & 0xff;
        if (ivLength < 12 || ivLength > 16 || value.remaining() <= ivLength) throw new IllegalArgumentException("Invalid session envelope");
        byte[] iv = new byte[ivLength];
        value.get(iv);
        byte[] ciphertext = new byte[value.remaining()];
        value.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
    }
}
