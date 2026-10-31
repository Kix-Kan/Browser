package com.kixkan.browser.prototype;

import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import java.util.concurrent.atomic.AtomicReference;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

/** Visible consent/cancel behavior; only current-session history is cleared here. */
@RunWith(AndroidJUnit4.class)
public final class BrowsingDataUiTest {
    @Test public void emptyAndUnclosedStorageChoicesCannotBeSubmitted() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openChoices();
            onView(withId(R.id.privacy_review)).check(matches(not(isEnabled())));
            onView(withId(R.id.privacy_cache)).perform(scrollTo(), click());
            onView(withId(R.id.privacy_review)).check(matches(not(isEnabled())));
            onView(withId(R.id.privacy_selection_status)).check(matches(withText(
                    "Also select Current tab & history; this prototype must close open pages safely before clearing storage.")));
            onView(withId(R.id.privacy_history)).perform(scrollTo(), click());
            onView(withId(R.id.privacy_review)).check(matches(isEnabled()));
            onView(withText("Cancel")).perform(click());
        }
    }

    @Test public void cancellingBothScreensKeepsTheExactLiveSession() {
        AtomicReference<GeckoSession> before = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> before.set(((GeckoView) activity.findViewById(R.id.gecko_view)).getSession()));
            openChoices();
            onView(withText("Cancel")).perform(click());
            scenario.onActivity(activity -> assertSame(before.get(),
                    ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession()));
            openChoices();
            onView(withId(R.id.privacy_history)).perform(scrollTo(), click());
            onView(withId(R.id.privacy_sites)).perform(scrollTo(), click());
            onView(withId(R.id.privacy_review)).perform(click());
            onView(withText("Keep data")).perform(click());
            onView(withText("Cancel")).perform(click());
            scenario.onActivity(activity -> {
                assertSame(before.get(), ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession());
                assertTrue(before.get().isOpen());
            });
        }
    }

    @Test public void confirmedHistoryResetClosesOldSessionAndOpensFreshHome() {
        AtomicReference<GeckoSession> before = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> before.set(((GeckoView) activity.findViewById(R.id.gecko_view)).getSession()));
            openChoices();
            onView(withId(R.id.privacy_history)).perform(scrollTo(), click());
            onView(withId(R.id.privacy_review)).perform(click());
            onView(withId(R.id.privacy_confirm_clear)).perform(click());
            onView(withText("Clear request completed")).check(matches(isDisplayed()));
            onView(withId(R.id.privacy_operation_status)).check(matches(withText(
                    "The current tab and its back/forward session history were closed. No browser-storage clear request was made.\n\nBookmarks and your KixKan account were kept.")));
            onView(withText("Done")).perform(click());
            scenario.onActivity(activity -> {
                GeckoSession after = ((GeckoView) activity.findViewById(R.id.gecko_view)).getSession();
                assertNotSame(before.get(), after);
                assertFalse(before.get().isOpen());
                assertNotNull(after);
                assertTrue(after.isOpen());
                assertFalse(activity.findViewById(R.id.back).isEnabled());
            });
        }
    }

    private void openChoices() {
        onView(withId(R.id.menu)).perform(click());
        onView(withText("Browsing data")).perform(click());
        onView(withText("Your browsing.\nYour choice.")).check(matches(isDisplayed()));
    }
}
