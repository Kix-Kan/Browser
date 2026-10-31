package com.kixkan.browser.prototype;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * Explicit fault injection, NOT an actual Gecko cleanup failure or erasure test.
 * Only this test's incomplete UI promise is substituted. No storage clear API,
 * account action, external page, credentials or persistent sentinel is used.
 */
@RunWith(AndroidJUnit4.class)
public final class BrowsingDataLifecycleTest {
    @Test public void simulatedEngineClearFailureSurvivesTwoPendingUiRecreationsWithoutReopeningPages()
            throws Exception {
        Field pendingField = field("pendingUiClear");
        BrowsingData.Selection selection = new BrowsingData.Selection(true, true, false);
        AtomicReference<GeckoResult<BrowsingData.Outcome>> injected = new AtomicReference<>();
        AtomicReference<GeckoSession> oldSession = new AtomicReference<>();
        AtomicReference<GeckoSession> recoveredSession = new AtomicReference<>();
        AtomicBoolean terminalSupplied = new AtomicBoolean();
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        try {
            await(scenario, activity -> "KixKan Browser".equals(stringField(activity, "currentTitle"))
                    && activity.findViewById(R.id.progress).getVisibility() != View.VISIBLE);
            scenario.onActivity(activity -> {
                assertNull("Do not replace another UI operation", read(pendingField, null));
                BrowsingDataController controller = (BrowsingDataController) value(activity, "dataController");
                assertFalse("Do not overlap a real engine operation", controller.isBusy());
                GeckoSession current = ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession();
                assertNotNull(current);
                assertTrue(current.isOpen());
                oldSession.set(current);

                GeckoResult<BrowsingData.Outcome> ownPromise = new GeckoResult<>(new Handler(Looper.getMainLooper()));
                injected.set(ownPromise);
                write(pendingField, null, ownPromise);
                invoke(activity, "setBrowsingEnabled", new Class<?>[]{boolean.class}, false);
                invoke(activity, "closeSessionForClear", new Class<?>[0]);
                invoke(activity, "watchClear", new Class<?>[]{GeckoResult.class}, ownPromise);
                assertPaused(activity, ownPromise, oldSession.get());
            });

            scenario.recreate();
            scenario.onActivity(activity -> assertPaused(activity, injected.get(), oldSession.get()));
            scenario.recreate();
            scenario.onActivity(activity -> assertPaused(activity, injected.get(), oldSession.get()));

            scenario.onActivity(activity -> {
                assertPaused(activity, injected.get(), oldSession.get());
                // Simulated exposed engine error: no actual clearData call ran.
                terminalSupplied.set(true);
                injected.get().completeExceptionally(new BrowsingData.ClearFailure(
                        BrowsingData.FailureStage.ENGINE_CLEAR, selection, true));
            });
            await(scenario, activity -> dialog(activity, "dataResult") != null
                    && dialog(activity, "dataResult").isShowing()
                    && activity.findViewById(R.id.gecko_view) != null
                    && ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession() != null);

            scenario.onActivity(activity -> {
                assertNull("The consumed UI operation must not remain pending", read(pendingField, null));
                AlertDialog progress = dialog(activity, "dataProgress");
                assertTrue(progress == null || !progress.isShowing());
                AlertDialog error = dialog(activity, "dataResult");
                assertNotNull(error);
                assertTrue(error.isShowing());
                assertTrue(containsText(error.getWindow().getDecorView(), "Clear request not confirmed"));
                assertFalse(containsText(error.getWindow().getDecorView(), "Clear request completed"));
                TextView status = error.findViewById(R.id.privacy_operation_status);
                assertNotNull(status);
                String message = status.getText().toString();
                assertTrue(message.contains("could not be confirmed"));
                assertTrue(message.contains("Some selected data may already have been removed"));
                assertFalse(message.contains("Gecko finished processing"));

                GeckoSession fresh = ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession();
                assertNotSame("Recovery must use a fresh session after the terminal event", oldSession.get(), fresh);
                assertTrue(fresh.isOpen());
                assertFalse(oldSession.get().isOpen());
                recoveredSession.set(fresh);
                assertNotNull(error.getButton(DialogInterface.BUTTON_POSITIVE));
                assertEquals("Done", error.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
                error.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            });
            await(scenario, activity -> (dialog(activity, "dataResult") == null
                    || !dialog(activity, "dataResult").isShowing())
                    && "KixKan Browser".equals(stringField(activity, "currentTitle"))
                    && activity.findViewById(R.id.progress).getVisibility() != View.VISIBLE);
            scenario.onActivity(activity -> {
                assertSame(recoveredSession.get(), ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession());
                assertTrue(activity.findViewById(R.id.address).isEnabled());
                assertTrue(activity.findViewById(R.id.menu).isEnabled());
                assertFalse(activity.findViewById(R.id.back).isEnabled());
                assertFalse(activity.findViewById(R.id.forward).isEnabled());
            });
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                GeckoResult<BrowsingData.Outcome> ownPromise = injected.get();
                if (ownPromise != null && read(pendingField, null) == ownPromise) {
                    // Restore only the exact field value installed by this test.
                    write(pendingField, null, null);
                }
                if (ownPromise != null && !terminalSupplied.getAndSet(true)) {
                    ownPromise.completeExceptionally(new BrowsingData.ClearFailure(
                            BrowsingData.FailureStage.ENGINE_CLEAR, selection, true));
                }
            });
            scenario.close();
        }
    }

    private static void assertPaused(MainActivity activity, GeckoResult<BrowsingData.Outcome> ownPromise,
            GeckoSession previous) {
        assertSame(ownPromise, value(activity, "pendingUiClear"));
        assertNull("No hidden live session may survive the close boundary", value(activity, "session"));
        assertNull("No session may open while the UI request is pending",
                ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession());
        assertFalse(previous.isOpen());
        for (int id : new int[]{R.id.address, R.id.back, R.id.forward, R.id.home,
                R.id.save_bookmark, R.id.bookmarks, R.id.account, R.id.menu}) {
            assertFalse("Browsing controls must remain disabled while waiting", activity.findViewById(id).isEnabled());
        }
        AlertDialog waiting = dialog(activity, "dataProgress");
        assertNotNull(waiting);
        assertTrue(waiting.isShowing());
        assertTrue(containsText(waiting.getWindow().getDecorView(), "Clearing browsing data"));
        assertTrue(containsText(waiting.getWindow().getDecorView(),
                "Waiting for the browser engine. Browsing is paused so websites cannot restore data. This operation cannot be undone or cancelled after confirmation."));
        assertNull("A pending operation cannot display a completed/error result yet", dialog(activity, "dataResult"));
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
        fail("Simulated pending-clear lifecycle condition did not complete within15s");
    }
    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsText(group.getChildAt(index), expected)) return true;
            }
        }
        return false;
    }
    private static AlertDialog dialog(MainActivity activity, String name) {
        return (AlertDialog) value(activity, name);
    }
    private static String stringField(MainActivity activity, String name) { return (String) value(activity, name); }
    private static Object value(MainActivity activity, String name) { return read(field(name), activity); }
    private static Field field(String name) {
        try { Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException error) { throw new AssertionError("Cannot inspect own clear-UI state", error); }
    }
    private static Object read(Field field, Object owner) {
        try { return field.get(owner); }
        catch (IllegalAccessException error) { throw new AssertionError("Cannot read own clear-UI state", error); }
    }
    private static void write(Field field, Object owner, Object value) {
        try { field.set(owner, value); }
        catch (IllegalAccessException error) { throw new AssertionError("Cannot restore own clear-UI state", error); }
    }
    private static void invoke(MainActivity activity, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(activity, arguments);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot invoke own clear-UI lifecycle boundary", error);
        }
    }
}
