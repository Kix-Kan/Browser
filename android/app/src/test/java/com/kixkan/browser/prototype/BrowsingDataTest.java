package com.kixkan.browser.prototype;

import org.junit.Test;
import java.util.EnumSet;
import static org.junit.Assert.*;

/** Pure selection/result tests: no Android runtime, browser profile or IO. */
public final class BrowsingDataTest {
    @Test public void emptySelectionIsInvalid() {
        BrowsingData.Selection selected = new BrowsingData.Selection(false, false, false);
        assertFalse(selected.isValid());
        assertEquals("Select at least one category.", selected.validationMessage());
        rejectFlags(selected);
    }
    @Test public void currentTabAndHistoryAloneIsValidWithoutStorageFlags() {
        BrowsingData.Selection selected = new BrowsingData.Selection(true, false, false);
        assertTrue(selected.isValid()); assertNull(selected.validationMessage());
        assertEquals(0L, selected.storageFlags());
        assertEquals(EnumSet.of(BrowsingData.Category.CURRENT_TAB_AND_HISTORY), selected.requestedCategories());
    }
    @Test public void cacheCannotSilentlyDeleteUncheckedHistory() {
        BrowsingData.Selection selected = new BrowsingData.Selection(false, true, false);
        assertFalse(selected.isValid());
        assertTrue(selected.validationMessage().contains("Also select Current tab & history"));
        rejectFlags(selected);
    }
    @Test public void siteStorageCannotSilentlyDeleteUncheckedHistory() {
        BrowsingData.Selection selected = new BrowsingData.Selection(false, false, true);
        assertFalse(selected.isValid()); rejectFlags(selected);
    }
    @Test public void bothStorageCategoriesStillRequireExplicitHistorySelection() {
        BrowsingData.Selection selected = new BrowsingData.Selection(false, true, true);
        assertFalse(selected.isValid()); rejectFlags(selected);
        assertFalse(selected.requestedCategories().contains(BrowsingData.Category.CURRENT_TAB_AND_HISTORY));
    }
    @Test public void cacheMappingIsOnlyNetworkAndImageBits() {
        assertEquals(6L, new BrowsingData.Selection(true, true, false).storageFlags());
        assertEquals(2L | 4L, BrowsingData.CACHE_FLAGS);
    }
    @Test public void siteMappingIsOnlyCookiesAndDomStorageBits() {
        assertEquals(17L, new BrowsingData.Selection(true, false, true).storageFlags());
        assertEquals(1L | 16L, BrowsingData.SITE_STORAGE_FLAGS);
    }
    @Test public void combinedMappingDoesNotUseAllSiteDataOrPermissionsAuthBits() {
        long flags = new BrowsingData.Selection(true, true, true).storageFlags();
        assertEquals(23L, flags);
        assertEquals(0L, flags & (32L | 64L | 128L | 256L | 512L));
        assertNotEquals(471L, flags);
        assertNotEquals(512L, flags);
    }
    @Test public void allEightCheckboxCombinationsFollowTheExplicitPolicy() {
        for (int mask = 0; mask < 8; mask++) {
            boolean history = (mask & 1) != 0, cache = (mask & 2) != 0, site = (mask & 4) != 0;
            BrowsingData.Selection selected = new BrowsingData.Selection(history, cache, site);
            assertEquals(history, selected.isValid());
            if (selected.isValid()) assertEquals((cache ? 6L : 0L) | (site ? 17L : 0L), selected.storageFlags());
        }
    }
    @Test public void timeRangeIsAlwaysAllTime() {
        assertEquals(BrowsingData.TimeRange.ALL_TIME, new BrowsingData.Selection(true, false, true).timeRange());
    }
    @Test public void requestedCategorySetCannotBeMutated() {
        BrowsingData.Selection selected = new BrowsingData.Selection(true, true, false);
        try { selected.requestedCategories().clear(); fail("Mutable selection set"); }
        catch (UnsupportedOperationException expected) { }
        assertEquals(2, selected.requestedCategories().size());
    }
    @Test public void confirmationDescribesOnlySelectedCategoryConsequencesAndPreservedData() {
        String text = new BrowsingData.Selection(true, false, false).confirmationText();
        assertTrue(text.contains("Time range: All time"));
        assertTrue(text.contains("Current tab & history"));
        assertFalse(text.contains(BrowsingData.Category.CACHE.consequence));
        assertFalse(text.contains(BrowsingData.Category.COOKIES_AND_SITE_STORAGE.consequence));
        assertTrue(text.contains("Bookmarks, the native KixKan account/session, saved passwords and downloaded files are not selected or erased"));
        assertTrue(text.contains("not secure erasure"));
    }
    @Test public void invalidSelectionCannotProduceAConfirmationOrCompletion() {
        BrowsingData.Selection invalid = new BrowsingData.Selection(false, true, false);
        try { invalid.confirmationText(); fail("Invalid confirmation produced"); }
        catch (IllegalArgumentException expected) { }
        try { BrowsingData.Outcome.requestCompleted(invalid); fail("Invalid completion produced"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void outcomeMeansEngineCompletionNotIndependentlyVerifiedErasure() {
        BrowsingData.Selection selected = new BrowsingData.Selection(true, false, true);
        BrowsingData.Outcome outcome = BrowsingData.Outcome.requestCompleted(selected);
        assertSame(selected, outcome.selection);
        assertEquals(17L, outcome.storageFlags);
        assertEquals(selected.requestedCategories(), outcome.requestedCategories);
        assertEquals(BrowsingData.Verification.ENGINE_COMPLETION_ONLY, outcome.verification);
        assertFalse(outcome.independentlyVerified);
        assertTrue(outcome.completionText().contains("finished processing"));
        assertTrue(outcome.completionText().contains("not been independently verified"));
    }
    @Test public void historyOnlyOutcomeDoesNotClaimStorageWasCleared() {
        BrowsingData.Outcome outcome = BrowsingData.Outcome.requestCompleted(new BrowsingData.Selection(true, false, false));
        assertTrue(outcome.completionText().contains("No browser-storage clear request was made"));
    }
    @Test public void outcomeCategoryMetadataCannotBeMutated() {
        BrowsingData.Outcome outcome = BrowsingData.Outcome.requestCompleted(new BrowsingData.Selection(true, true, true));
        try { outcome.requestedCategories.remove(BrowsingData.Category.CACHE); fail("Mutable outcome set"); }
        catch (UnsupportedOperationException expected) { }
    }
    @Test public void selectionEqualityIncludesEveryCheckbox() {
        BrowsingData.Selection first = new BrowsingData.Selection(true, true, false);
        BrowsingData.Selection equal = new BrowsingData.Selection(true, true, false);
        assertEquals(first, equal); assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, new BrowsingData.Selection(true, false, false));
        assertNotEquals(first, new BrowsingData.Selection(true, true, true));
    }
    @Test public void failuresAreTypedAndDoNotClaimAtomicRollback() {
        BrowsingData.Selection selected = new BrowsingData.Selection(true, true, false);
        BrowsingData.ClearFailure failure = new BrowsingData.ClearFailure(BrowsingData.FailureStage.ENGINE_CLEAR, selected, true);
        assertEquals(BrowsingData.FailureStage.ENGINE_CLEAR, failure.stage);
        assertSame(selected, failure.selection);
        assertTrue(failure.sessionBoundaryCompleted);
        assertTrue(failure.getMessage().contains("some storage may already have changed"));
        assertTrue(failure.getMessage().contains("No automatic retry"));
        assertNull(failure.getCause());
    }
    @Test public void busyFailurePreservesTheExistingOperationBoundary() {
        BrowsingData.ClearFailure failure = new BrowsingData.ClearFailure(BrowsingData.FailureStage.BUSY,
                new BrowsingData.Selection(true, false, false), false);
        assertFalse(failure.sessionBoundaryCompleted);
        assertTrue(failure.getMessage().contains("already running"));
    }
    @Test public void closingFailureDoesNotClaimStorageCompletion() {
        BrowsingData.ClearFailure failure = new BrowsingData.ClearFailure(BrowsingData.FailureStage.CLOSE_SESSIONS,
                new BrowsingData.Selection(true, false, true), false);
        assertFalse(failure.sessionBoundaryCompleted);
        assertTrue(failure.getMessage().contains("browser storage was not cleared"));
        assertTrue(failure.getMessage().contains("may already be gone"));
    }
    private void rejectFlags(BrowsingData.Selection selected) {
        try { selected.storageFlags(); fail("Invalid selection received clear flags"); }
        catch (IllegalArgumentException expected) { }
    }
}
