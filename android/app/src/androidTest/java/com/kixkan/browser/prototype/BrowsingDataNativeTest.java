package com.kixkan.browser.prototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.StorageController;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real pinned-engine storage tests. The only server is bounded, loopback-only and synthetic. */
@RunWith(AndroidJUnit4.class)
public final class BrowsingDataNativeTest {
    @Test public void historyOnlyClosesSessionButPreservesSiteDataAndNetworkCache() throws Exception {
        try (Fixture fixture = new Fixture(); NativeSentinels sentinels = new NativeSentinels()) {
            fixture.seedWithHistory();
            GeckoSession previous = fixture.probe.session;
            BrowsingData.Outcome outcome = fixture.clear(new BrowsingData.Selection(true, false, false));
            assertEquals(0L, outcome.storageFlags);
            assertFalse(outcome.independentlyVerified);
            assertEquals(BrowsingData.Verification.ENGINE_COMPLETION_ONLY, outcome.verification);
            onMain(() -> assertFalse(previous.isOpen()));
            fixture.inspectInFreshSession(true, true);
            assertEquals("History-only must preserve the HTTP cache", 1, fixture.server.assetRequests.get());
            sentinels.assertPreserved();
        }
    }

    @Test public void cacheClearPreservesCookiesAndLocalStorage() throws Exception {
        try (Fixture fixture = new Fixture(); NativeSentinels sentinels = new NativeSentinels()) {
            fixture.seedWithHistory();
            BrowsingData.Outcome outcome = fixture.clear(new BrowsingData.Selection(true, true, false));
            assertEquals(StorageController.ClearFlags.ALL_CACHES, outcome.storageFlags);
            fixture.inspectInFreshSession(true, true);
            assertEquals("The cached response must be fetched again after cache clear", 2,
                    fixture.server.assetRequests.get());
            sentinels.assertPreserved();
        }
    }

    @Test public void siteStorageClearRemovesCookieAndLocalStorageButPreservesNetworkCache() throws Exception {
        try (Fixture fixture = new Fixture(); NativeSentinels sentinels = new NativeSentinels()) {
            fixture.seedWithHistory();
            BrowsingData.Outcome outcome = fixture.clear(new BrowsingData.Selection(true, false, true));
            assertEquals(StorageController.ClearFlags.COOKIES | StorageController.ClearFlags.DOM_STORAGES,
                    outcome.storageFlags);
            fixture.inspectInFreshSession(false, false);
            assertEquals("Site-storage clear must not request clearing the HTTP network cache", 1,
                    fixture.server.assetRequests.get());
            sentinels.assertPreserved();
        }
    }

    @Test public void invalidSelectionAndFailedCloseBoundaryPreserveRealDataWithoutRetry() throws Exception {
        try (Fixture fixture = new Fixture(); NativeSentinels sentinels = new NativeSentinels()) {
            fixture.seedWithHistory();
            AtomicInteger closeCalls = new AtomicInteger();
            Throwable invalid = awaitFailure(() -> fixture.controller.clear(
                    new BrowsingData.Selection(false, true, false), closeCalls::incrementAndGet));
            assertEquals(BrowsingData.FailureStage.VALIDATION, ((BrowsingData.ClearFailure) invalid).stage);
            assertEquals(0, closeCalls.get());
            Throwable failedBoundary = awaitFailure(() -> fixture.controller.clear(
                    new BrowsingData.Selection(true, true, true), () -> {
                        closeCalls.incrementAndGet();
                        throw new IllegalStateException("Synthetic session boundary failure");
                    }));
            BrowsingData.ClearFailure failure = (BrowsingData.ClearFailure) failedBoundary;
            assertEquals(BrowsingData.FailureStage.CLOSE_SESSIONS, failure.stage);
            assertFalse(failure.sessionBoundaryCompleted);
            assertFalse(fixture.controller.isBusy());
            onMain(() -> assertTrue(fixture.probe.session.isOpen()));
            fixture.inspectExistingSession(true, true);
            assertEquals(1, closeCalls.get());
            assertEquals(1, fixture.server.assetRequests.get());
            sentinels.assertPreserved();
        }
    }

    @Test public void secondControllerForSameRuntimeRejectsBusyRequestBeforeClosingAnything() throws Exception {
        try (Fixture fixture = new Fixture(); NativeSentinels sentinels = new NativeSentinels()) {
            fixture.seedWithHistory();
            BrowsingDataController second = new BrowsingDataController(fixture.runtime);
            AtomicReference<GeckoResult<BrowsingData.Outcome>> rejected = new AtomicReference<>();
            AtomicInteger rejectedCloseCalls = new AtomicInteger();
            awaitResult(() -> fixture.controller.clear(new BrowsingData.Selection(true, false, false), () -> {
                assertTrue(second.isBusy());
                rejected.set(second.clear(new BrowsingData.Selection(true, false, false),
                        rejectedCloseCalls::incrementAndGet));
                fixture.probe.closeOnMain();
            }));
            Throwable failure = awaitFailure(rejected::get);
            assertEquals(BrowsingData.FailureStage.BUSY, ((BrowsingData.ClearFailure) failure).stage);
            assertEquals(0, rejectedCloseCalls.get());
            assertFalse(second.isBusy());
            fixture.inspectInFreshSession(true, true);
            assertEquals(1, fixture.server.assetRequests.get());
            sentinels.assertPreserved();
        }
    }

    private interface ResultOperation<T> { GeckoResult<T> run(); }
    private static <T> T awaitResult(ResultOperation<T> operation) throws Exception {
        ResultBox<T> result = subscribe(operation);
        if (result.error.get() != null) throw new AssertionError("Expected engine completion, not failure",
                result.error.get());
        return result.value.get();
    }
    private static Throwable awaitFailure(ResultOperation<BrowsingData.Outcome> operation) throws Exception {
        ResultBox<BrowsingData.Outcome> result = subscribe(operation);
        assertTrue("Expected a typed browsing-data failure", result.error.get() instanceof BrowsingData.ClearFailure);
        return result.error.get();
    }
    private static <T> ResultBox<T> subscribe(ResultOperation<T> operation) throws Exception {
        ResultBox<T> box = new ResultBox<>();
        onMain(() -> operation.run().accept(value -> {
            box.value.set(value); box.done.countDown();
        }, error -> {
            box.error.set(error); box.done.countDown();
        }));
        assertTrue("Engine request did not finish within20s; this is not a cancellation or rollback",
                box.done.await(20, TimeUnit.SECONDS));
        return box;
    }
    private static final class ResultBox<T> {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<T> value = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
    }
    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
    private static GeckoRuntime existingRuntime() {
        AtomicReference<GeckoRuntime> result = new AtomicReference<>();
        // Gecko permits only one runtime in this process. Reuse the application-owned
        // one and close its Activity/session before creating the synthetic sessions.
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Field field = MainActivity.class.getDeclaredField("runtime");
                    field.setAccessible(true);
                    result.set((GeckoRuntime) field.get(null));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Cannot inspect own application runtime", error);
                }
            });
        }
        assertNotNull(result.get());
        return result.get();
    }

    private static final class Fixture implements AutoCloseable {
        final GeckoRuntime runtime = existingRuntime();
        final BrowsingDataController controller = new BrowsingDataController(runtime);
        final FixtureServer server = new FixtureServer();
        Probe probe;
        Fixture() throws IOException { assertFalse(controller.isBusy()); }
        void seedWithHistory() {
            openProbe();
            probe.load(server.url("/seed"), "KIXKAN_FIXTURE:C1L1A1");
            inspectExistingSession(true, true);
            probe.awaitBackState(true);
            assertTrue("Fixture must establish real back history before clearing", probe.canGoBack.get());
            assertEquals("Two fixture pages should share a genuinely cached HTTP asset", 1,
                    server.assetRequests.get());
        }
        BrowsingData.Outcome clear(BrowsingData.Selection selection) throws Exception {
            BrowsingData.Outcome result = awaitResult(() -> controller.clear(selection, probe::closeOnMain));
            assertFalse(controller.isBusy());
            return result;
        }
        void openProbe() {
            onMain(() -> { probe = new Probe(); probe.session.open(runtime); });
        }
        void inspectInFreshSession(boolean cookie, boolean storage) {
            openProbe();
            inspectExistingSession(cookie, storage);
            probe.awaitBackState(false);
            assertFalse("Fresh session must not retain back history", probe.canGoBack.get());
            assertFalse("Fresh session must not retain forward history", probe.canGoForward.get());
        }
        void inspectExistingSession(boolean cookie, boolean storage) {
            probe.load(server.url("/inspect"), "KIXKAN_FIXTURE:C" + (cookie ? "1" : "0")
                    + "L" + (storage ? "1" : "0") + "A1");
            assertEquals("The server must independently observe the expected request cookie", cookie,
                    server.inspectionHadCookie.get());
            server.assertHealthy();
        }
        @Override public void close() throws Exception {
            if (probe != null) onMain(probe::closeOnMain);
            server.close();
            // Only this fixed synthetic loopback host, never profile/database deletion.
            awaitResult(() -> runtime.getStorageController().clearDataFromHost("127.0.0.1",
                    StorageController.ClearFlags.COOKIES | StorageController.ClearFlags.DOM_STORAGES
                            | StorageController.ClearFlags.ALL_CACHES));
        }
    }

    private static final class Probe {
        final GeckoSession session = new GeckoSession();
        final AtomicReference<String> title = new AtomicReference<>("");
        final AtomicBoolean canGoBack = new AtomicBoolean();
        final AtomicBoolean canGoForward = new AtomicBoolean();
        final AtomicInteger backEvents = new AtomicInteger();
        final AtomicBoolean failed = new AtomicBoolean();
        Probe() {
            session.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override public void onTitleChange(GeckoSession session, String value) { title.set(value); }
            });
            session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override public void onCanGoBack(GeckoSession session, boolean value) {
                    canGoBack.set(value); backEvents.incrementAndGet();
                }
                @Override public void onCanGoForward(GeckoSession session, boolean value) { canGoForward.set(value); }
            });
            session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                @Override public void onPageStop(GeckoSession session, boolean success) { if (!success) failed.set(true); }
            });
        }
        void load(String url, String expectedTitle) {
            title.set(""); failed.set(false);
            onMain(() -> session.loadUri(url));
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (SystemClock.uptimeMillis() < deadline) {
                if (expectedTitle.equals(title.get())) return;
                assertFalse("Synthetic loopback load failed; production HTTPS/TLS settings remain unchanged", failed.get());
                assertFalse("Synthetic JavaScript fixture failed", "KIXKAN_FIXTURE:ERROR".equals(title.get()));
                SystemClock.sleep(25);
            }
            fail("Synthetic storage title did not reach the expected state within15s");
        }
        void closeOnMain() { if (session.isOpen()) session.close(); }
        void awaitBackState(boolean expected) {
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (SystemClock.uptimeMillis() < deadline) {
                if (backEvents.get() > 0 && canGoBack.get() == expected) return;
                SystemClock.sleep(25);
            }
            fail("Synthetic session did not report the expected real back-history state within5s");
        }
    }

    /** Exact synthetic row/key only; no account token, password or other user data is read. */
    private static final class NativeSentinels implements AutoCloseable {
        final BookmarkStore bookmarks;
        final SharedPreferences preferences;
        final String marker = UUID.randomUUID().toString();
        final String url = "https://example.invalid/kixkan-native-test/" + marker;
        final String bookmarkId;
        NativeSentinels() {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            preferences = context.getSharedPreferences("kixkan-browsing-data-native-test", Context.MODE_PRIVATE);
            assertTrue(preferences.edit().putString(marker, "preserve").commit());
            bookmarks = new BookmarkStore(context);
            bookmarks.add("Synthetic native-test bookmark", url);
            bookmarkId = bookmarks.active().stream().filter(entry -> url.equals(entry.url))
                    .findFirst().orElseThrow(() -> new AssertionError("Synthetic bookmark was not saved")).id;
        }
        void assertPreserved() {
            assertEquals("preserve", preferences.getString(marker, null));
            assertTrue(bookmarks.active().stream().anyMatch(entry -> bookmarkId.equals(entry.id) && url.equals(entry.url)));
        }
        @Override public void close() {
            bookmarks.getWritableDatabase().delete("bookmarks", "id = ?", new String[]{bookmarkId});
            bookmarks.close();
            assertTrue(preferences.edit().remove(marker).commit());
        }
    }

    private static final class FixtureServer implements AutoCloseable {
        final String token = UUID.randomUUID().toString().replace("-", "");
        final String cookie = "kixkan_" + token + "=yes";
        final AtomicInteger assetRequests = new AtomicInteger();
        final AtomicBoolean inspectionHadCookie = new AtomicBoolean();
        final AtomicReference<Exception> error = new AtomicReference<>();
        final ServerSocket socket = new ServerSocket();
        final Thread worker;
        volatile boolean closed;
        FixtureServer() throws IOException {
            socket.bind(new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
            socket.setSoTimeout(250);
            worker = new Thread(this::serve, "kixkan-synthetic-storage-fixture");
            worker.setDaemon(true);
            worker.start();
        }
        String url(String path) { return "http://127.0.0.1:" + socket.getLocalPort() + path; }
        void serve() {
            while (!closed) {
                try (Socket client = socket.accept()) {
                    client.setSoTimeout(2000);
                    String request = readHeaders(client.getInputStream());
                    if (request == null) continue; // An unused speculative connection sends no request.
                    String first = request.substring(0, request.indexOf("\r\n"));
                    if (!first.startsWith("GET ") || !first.endsWith(" HTTP/1.1")) { reply(client, 400, "", ""); continue; }
                    String path = first.substring(4, first.length() - 9);
                    if (path.equals("/asset-" + token + ".txt")) {
                        assetRequests.incrementAndGet();
                        reply(client, 200, token, "Content-Type: text/plain\r\nCache-Control: public, max-age=3600\r\n");
                    } else if (path.equals("/seed") || path.equals("/inspect")) {
                        boolean seed = path.equals("/seed");
                        if (!seed) inspectionHadCookie.set(java.util.Arrays.stream(request.split("\r\n")).anyMatch(line ->
                                line.regionMatches(true, 0, "Cookie:", 0, 7)
                                        && java.util.Arrays.stream(line.substring(7).split(";"))
                                                .anyMatch(value -> cookie.equals(value.trim()))));
                        reply(client, 200, html(seed), "Content-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\n"
                                + (seed ? "Set-Cookie: " + cookie + "; Path=/; Max-Age=3600; SameSite=Lax\r\n" : ""));
                    } else { reply(client, 404, "Not found", "Cache-Control: no-store\r\n"); }
                } catch (SocketTimeoutException ignored) {
                    // Bounded idle accept/read, not an external connection service.
                } catch (SocketException failure) {
                    if (!closed) error.compareAndSet(null, failure);
                } catch (Exception failure) { error.compareAndSet(null, failure); }
            }
        }
        String html(boolean seed) {
            return "<!doctype html><meta charset='utf-8'><title>Fixture pending</title><script>"
                    + (seed ? "localStorage.setItem('" + token + "','yes');" : "")
                    + "fetch('/asset-" + token + ".txt').then(r=>r.text()).then(a=>{"
                    + "document.title='KIXKAN_FIXTURE:C'+(document.cookie.split(';').some(x=>x.trim()==='" + cookie + "')?'1':'0')"
                    + "+'L'+(localStorage.getItem('" + token + "')==='yes'?'1':'0')+'A'+(a==='" + token + "'?'1':'0');"
                    + "}).catch(()=>document.title='KIXKAN_FIXTURE:ERROR');</script>";
        }
        static String readHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int state = 0;
            while (bytes.size() < 8192) {
                int value = input.read();
                if (value < 0 && bytes.size() == 0) return null;
                if (value < 0) throw new IOException("Incomplete synthetic request");
                bytes.write(value);
                int expected = state == 0 || state == 2 ? '\r' : '\n';
                state = value == expected ? state + 1 : (value == '\r' ? 1 : 0);
                if (state == 4) return bytes.toString(StandardCharsets.US_ASCII.name());
            }
            throw new IOException("Synthetic request exceeds limit");
        }
        static void reply(Socket client, int status, String body, String extra) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            SimpleDateFormat date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            date.setTimeZone(TimeZone.getTimeZone("GMT"));
            String header = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\nDate: "
                    + date.format(new Date()) + "\r\nConnection: close\r\nContent-Length: " + bytes.length + "\r\n" + extra + "\r\n";
            client.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().write(bytes);
            client.getOutputStream().flush();
        }
        void assertHealthy() { assertNull("Synthetic server encountered an IO error", error.get()); }
        @Override public void close() throws Exception {
            closed = true;
            socket.close();
            worker.join(3000);
            assertFalse("Synthetic loopback server must terminate", worker.isAlive());
            assertHealthy();
        }
    }
}
