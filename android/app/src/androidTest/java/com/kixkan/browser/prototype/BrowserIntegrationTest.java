package com.kixkan.browser.prototype;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import static org.junit.Assert.*;

/** APK/Android integration checks; no page/account network or credentials used. */
@RunWith(AndroidJUnit4.class)
public final class BrowserIntegrationTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void appHasKixkanLabelIconAndSingleTaskUrlActivity() throws Exception {
        Context context = context();
        PackageManager manager = context.getPackageManager();
        assertEquals("KixKan Browser", manager.getApplicationLabel(context.getApplicationInfo()).toString());
        assertTrue(context.getApplicationInfo().icon != 0);
        ActivityInfo info = manager.getActivityInfo(new ComponentName(context, MainActivity.class), 0);
        assertTrue(info.exported);
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode);
    }
    @Test public void manifestResolvesBothBrowsableHttpAndHttps() {
        for (String scheme : new String[]{"http", "https"}) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://example.com/"))
                    .addCategory(Intent.CATEGORY_BROWSABLE).addCategory(Intent.CATEGORY_DEFAULT)
                    .setPackage(context().getPackageName());
            assertNotNull(context().getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY));
        }
    }
    @Test public void intentDataAndLegacyExtraShareTheSameStrictGate() {
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/path"));
        assertEquals("https://example.com/path", BrowserIntents.requestedHttpUrl(web));
        Intent bookmark = new Intent().putExtra(MainActivity.EXTRA_URL, "https://example.com/path");
        assertEquals("https://example.com/path", BrowserIntents.requestedHttpUrl(bookmark));
        for (String value : new String[]{"javascript:alert(1)", NavigationInput.START_URI, "file:///tmp/a", "content://contacts/data/42"}) {
            rejectIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
            rejectIntent(new Intent().putExtra(MainActivity.EXTRA_URL, value));
        }
        rejectIntent(new Intent(Intent.ACTION_VIEW));
        assertNull(BrowserIntents.requestedHttpUrl(new Intent(Intent.ACTION_MAIN)));
    }
    @Test public void packagedDayAndNightPalettesMatchKixkanMobile() {
        Configuration day = new Configuration(context().getResources().getConfiguration());
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        Configuration night = new Configuration(day);
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        assertEquals(0xfff4f6fb, context().createConfigurationContext(day).getColor(R.color.kixkan_night));
        assertEquals(0xff0c1322, context().createConfigurationContext(night).getColor(R.color.kixkan_night));
        assertEquals(0xff101b31, context().createConfigurationContext(day).getColor(R.color.kixkan_ink));
        assertEquals(0xffeef3ff, context().createConfigurationContext(night).getColor(R.color.kixkan_ink));
        assertEquals(GeckoRuntimeSettings.COLOR_SCHEME_LIGHT, MainActivity.webColorScheme(day));
        assertEquals(GeckoRuntimeSettings.COLOR_SCHEME_DARK, MainActivity.webColorScheme(night));
    }
    @Test public void packagedStartPageIsAndroidOwnedAndUsesApprovedSymbol() throws Exception {
        String html;
        try (InputStream page = context().getAssets().open("kixkan-start/index.html")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = page.read(buffer)) != -1) {
                assertTrue("Unexpected oversized start page", bytes.size() + count <= 65536);
                bytes.write(buffer, 0, count);
            }
            html = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        assertTrue(html.contains("KixKan Browser"));
        assertTrue(html.contains("Developed by Broadway Pixels"));
        assertTrue(html.contains("https://duckduckgo.com/"));
        assertFalse(html.contains("about:protections"));
        assertFalse(html.contains("about:preferences"));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream image = context().getAssets().open("kixkan-start/kixkan-symbol.png")) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = image.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hash = new StringBuilder();
        for (byte item : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
        assertEquals("02582f057f04d5fd1427592f8fd71c5209fc456e989ae365514aa0a30a0ed05d", hash.toString());
    }
    private void rejectIntent(Intent intent) {
        try { BrowserIntents.requestedHttpUrl(intent); fail("Unsafe Android URL accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
