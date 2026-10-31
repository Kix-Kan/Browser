package com.kixkan.browser.prototype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

public final class ExternalNavigationTest {
    @Test public void httpsUrlPreservesEscapedPathQueryAndFragment() {
        assertEquals("https://example.com/a%20b?q=one%2Btwo#section",
                ExternalNavigation.resolveHttpUrl("https://example.com/a%20b?q=one%2Btwo#section"));
    }
    @Test public void httpIsUpgradedBeforeItReachesTheEngine() {
        assertEquals("https://example.com/path", ExternalNavigation.resolveHttpUrl("http://example.com/path"));
    }
    @Test public void schemesAreCaseInsensitiveAndNormalized() {
        assertEquals("https://EXAMPLE.COM/path", ExternalNavigation.resolveHttpUrl("HTTP://EXAMPLE.COM/path"));
    }
    @Test public void explicitOrdinaryPortIsPreserved() {
        assertEquals("https://example.com:8443/path", ExternalNavigation.resolveHttpUrl("https://example.com:8443/path"));
    }
    @Test public void localAssetsAndPrivilegedSchemesAreRejected() {
        reject(NavigationInput.START_URI);
        reject("resource://android/assets/secret"); reject("file:///data/local/file");
        reject("content://contacts/data/42"); reject("about:config"); reject("javascript:alert(1)");
        reject("data:text/html,hello"); reject("intent://example.com");
    }
    @Test public void missingSchemeOpaqueLinksAndSearchesAreRejected() {
        reject("example.com"); reject("//example.com"); reject("https:example.com"); reject("private browser");
    }
    @Test public void controlWhitespaceAndBackslashAreRejected() {
        reject(" https://example.com"); reject("https://example.com ");
        reject("https://example.com\n/path"); reject("https://example.com\t/path");
        reject("https://example.com\\@evil.example");
    }
    @Test public void embeddedCredentialsAreRejected() {
        reject("https://user:password@example.com/path"); reject("https://user@example.com");
    }
    @Test public void absentHostsAndInvalidPortsAreRejected() {
        reject("https:///path"); reject("https://"); reject("https://example.com:0");
        reject("https://example.com:65536"); reject("https://example.com:bad");
    }
    @Test public void malformedEscapesAndNullOrOversizedInputAreRejected() {
        reject(null); reject(""); reject("https://example.com/%zz");
        reject("https://example.com/" + "a".repeat(8192));
    }
    private void reject(String value) {
        try { ExternalNavigation.resolveHttpUrl(value); fail("Unsupported external URL accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
