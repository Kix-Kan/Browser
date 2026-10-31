package com.kixkan.browser.prototype;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NavigationInputTest {
    @Test
    public void emptyInputReturnsLocalStartPage() {
        assertEquals(NavigationInput.START_URI, NavigationInput.resolve("   "));
    }

    @Test
    public void domainGetsSecureScheme() {
        assertEquals("https://example.com", NavigationInput.resolve("example.com"));
    }

    @Test
    public void explicitWebUrlIsPreserved() {
        assertEquals(
                "https://example.com/path?q=one",
                NavigationInput.resolve("https://example.com/path?q=one"));
    }

    @Test
    public void wordsBecomeEncodedDuckDuckGoSearch() {
        assertEquals(
                "https://duckduckgo.com/?q=private+browser",
                NavigationInput.resolve("private browser"));
    }

    @Test
    public void nonWebSchemeCannotExecuteAsNavigation() {
        assertEquals(
                "https://duckduckgo.com/?q=javascript%3Aalert%281%29",
                NavigationInput.resolve("javascript:alert(1)"));
    }
}
