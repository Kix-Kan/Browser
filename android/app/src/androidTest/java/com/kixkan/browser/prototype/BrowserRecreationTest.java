package com.kixkan.browser.prototype;

import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real engine lifecycle/history test using only a fixed local HTML document. */
@RunWith(AndroidJUnit4.class)
public final class BrowserRecreationTest {
    private static final String PROBE_TITLE = "KixKan configuration continuity probe";
    private static final String PROBE_HTML = "<!doctype html><html><head><title>"
            + PROBE_TITLE + "</title><meta name='color-scheme' content='light dark'></head>"
            + "<body><h1>Local configuration continuity probe</h1></body></html>";

    @Test public void recreationKeepsTheLiveSessionPageAndUsableHistoryThenClosesIt() {
        AtomicReference<GeckoSession> originalSession = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            await(scenario, activity -> title(activity).equals("KixKan Browser") && !loading(activity));
            AtomicReference<MainActivity> originalActivity = new AtomicReference<>();
            AtomicReference<String> originalAddress = new AtomicReference<>();
            scenario.onActivity(activity -> {
                originalActivity.set(activity);
                GeckoSession session = ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession();
                assertNotNull(session);
                originalSession.set(session);
                // Native test fixture only: never supplied through exported Intent
                // data/extras, whose data: rejection stays covered separately.
                session.load(new GeckoSession.Loader().data(PROBE_HTML, "text/html"));
            });
            await(scenario, activity -> title(activity).equals(PROBE_TITLE) && !loading(activity)
                    && address(activity).startsWith("data:") && activity.findViewById(R.id.back).isEnabled());
            scenario.onActivity(activity -> originalAddress.set(address(activity)));

            scenario.recreate();

            scenario.onActivity(activity -> {
                assertNotSame("Android must recreate the Activity", originalActivity.get(), activity);
                assertSame("The live GeckoSession must survive configuration recreation", originalSession.get(),
                        ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession());
                assertEquals(PROBE_TITLE, title(activity));
                assertEquals("Authoritative location must survive recreation", originalAddress.get(), location(activity));
                assertEquals(originalAddress.get(), address(activity));
                assertTrue(activity.findViewById(R.id.back).isEnabled());
                assertFalse(activity.findViewById(R.id.forward).isEnabled());
                assertFalse(loading(activity));
                activity.findViewById(R.id.back).performClick();
            });
            await(scenario, activity -> title(activity).equals("KixKan Browser") && address(activity).isEmpty()
                    && !loading(activity) && activity.findViewById(R.id.forward).isEnabled());
            scenario.onActivity(activity -> activity.findViewById(R.id.forward).performClick());
            await(scenario, activity -> title(activity).equals(PROBE_TITLE) && !loading(activity)
                    && originalAddress.get().equals(address(activity)));
            scenario.onActivity(activity -> ((GeckoView) activity.findViewById(R.id.gecko_view))
                    .getSession().loadUri("about:blank"));
            await(scenario, activity -> "about:blank".equals(location(activity))
                    && "about:blank".equals(address(activity)) && !loading(activity));
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertNotNull(originalSession.get());
            assertFalse("Terminal Activity close must close the engine session", originalSession.get().isOpen());
        });
    }

    private interface Condition { boolean met(MainActivity activity); }
    private static void await(ActivityScenario<MainActivity> scenario, Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> ready.set(condition.met(activity)));
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail("Local engine configuration/history condition did not complete within15s");
    }
    private static String address(MainActivity activity) {
        return ((EditText) activity.findViewById(R.id.address)).getText().toString();
    }
    private static boolean loading(MainActivity activity) {
        return activity.findViewById(R.id.progress).getVisibility() == View.VISIBLE;
    }
    private static String title(MainActivity activity) {
        return stringState(activity, "currentTitle");
    }
    private static String location(MainActivity activity) {
        return stringState(activity, "currentUrl");
    }
    private static String stringState(MainActivity activity, String name) {
        try {
            // Own-app debug field only, not a hidden Android/platform API.
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return (String) field.get(activity);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot inspect own browser state", error);
        }
    }
}
