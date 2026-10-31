package com.kixkan.browser.prototype;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

final class NavigationInput {
    static final String START_URI = "resource://android/assets/kixkan-start/index.html";
    private static final String SEARCH_URI = "https://duckduckgo.com/?q=";

    private NavigationInput() {}

    static String resolve(String rawInput) {
        String input = rawInput.trim();
        if (input.isEmpty()) {
            return START_URI;
        }
        if (looksLikeSearch(input)) {
            return SEARCH_URI + urlEncode(input);
        }
        if (isWebUri(input)) {
            return input;
        }
        return "https://" + input;
    }

    private static boolean looksLikeSearch(String input) {
        return input.indexOf(' ') >= 0
                || (hasScheme(input) && !isWebUri(input))
                || (!input.contains(".") && !hasScheme(input));
    }

    private static boolean hasScheme(String input) {
        return input.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*$");
    }

    private static boolean isWebUri(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is required by Android", impossible);
        }
    }
}
