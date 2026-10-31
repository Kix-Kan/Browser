package com.kixkan.browser.prototype;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** External app links are URLs, never searches, local resources or script input. */
final class ExternalNavigation {
    private ExternalNavigation() {}

    static String resolveHttpUrl(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 8192) throw new IllegalArgumentException("Unsupported web link");
        for (int i = 0; i < raw.length(); i++) {
            char value = raw.charAt(i);
            if (Character.isISOControl(value) || Character.isWhitespace(value) || value == '\\') {
                throw new IllegalArgumentException("Unsupported web link");
            }
        }
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || uri.isOpaque()
                    || uri.getHost() == null || uri.getHost().isEmpty() || uri.getRawUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Unsupported web link");
            }
            // Preserve escaped path/query/fragment exactly, upgrading the scheme.
            int authorityStart = raw.indexOf("://");
            if (authorityStart < 0) throw new IllegalArgumentException("Unsupported web link");
            return "https://" + raw.substring(authorityStart + 3);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Unsupported web link", invalid);
        }
    }
}
