package com.kixkan.browser.prototype;

import android.content.Intent;

/** Every exported incoming URL, including legacy bookmark extras, shares a gate. */
final class BrowserIntents {
    private BrowserIntents() {}

    static String requestedHttpUrl(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return ExternalNavigation.resolveHttpUrl(intent.getDataString());
        }
        if (intent.hasExtra(MainActivity.EXTRA_URL)) {
            return ExternalNavigation.resolveHttpUrl(intent.getStringExtra(MainActivity.EXTRA_URL));
        }
        return null;
    }
}
