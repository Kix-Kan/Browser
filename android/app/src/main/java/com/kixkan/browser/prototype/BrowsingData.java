package com.kixkan.browser.prototype;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Pure all-time selection and honest completion model; no Android or storage IO. */
public final class BrowsingData {
    // Pinned GeckoView159 mappings, checked against named engine flags by the controller.
    public static final long CACHE_FLAGS = 2L | 4L;
    public static final long SITE_STORAGE_FLAGS = 1L | 16L;
    private BrowsingData() {}

    public enum Category {
        CURRENT_TAB_AND_HISTORY("Current tab & history",
                "Closes the current tab and discards this session's back/forward history."),
        CACHE("Cached images and files",
                "Requests clearing network and image caches for all sites. Pages may load more slowly."),
        COOKIES_AND_SITE_STORAGE("Cookies and site storage",
                "Requests clearing cookies and DOM storage for all sites. This may sign you out of websites and remove offline site data.");

        public final String label;
        public final String consequence;
        Category(String label, String consequence) { this.label = label; this.consequence = consequence; }
    }

    public enum TimeRange { ALL_TIME }
    public enum Verification { ENGINE_COMPLETION_ONLY }
    public enum FailureStage { VALIDATION, BUSY, CLOSE_SESSIONS, ENGINE_CLEAR }

    /** Invalid UI selections may be represented, but can never reach storage IO. */
    public static final class Selection {
        public final boolean history;
        public final boolean cache;
        public final boolean siteData;

        public Selection(boolean history, boolean cache, boolean siteData) {
            this.history = history;
            this.cache = cache;
            this.siteData = siteData;
        }

        public TimeRange timeRange() { return TimeRange.ALL_TIME; }
        public boolean isValid() { return validationMessage() == null; }
        public String validationMessage() {
            if (!history && !cache && !siteData) return "Select at least one category.";
            if ((cache || siteData) && !history) {
                return "Also select Current tab & history; this prototype must close open pages safely before clearing storage.";
            }
            return null;
        }

        public void validate() {
            String invalid = validationMessage();
            if (invalid != null) throw new IllegalArgumentException(invalid);
        }

        public long storageFlags() {
            validate();
            return (cache ? CACHE_FLAGS : 0L) | (siteData ? SITE_STORAGE_FLAGS : 0L);
        }

        public Set<Category> requestedCategories() {
            EnumSet<Category> selected = EnumSet.noneOf(Category.class);
            if (history) selected.add(Category.CURRENT_TAB_AND_HISTORY);
            if (cache) selected.add(Category.CACHE);
            if (siteData) selected.add(Category.COOKIES_AND_SITE_STORAGE);
            return Collections.unmodifiableSet(selected);
        }

        public String confirmationText() {
            validate();
            StringBuilder message = new StringBuilder("Time range: All time.\n\n");
            for (Category category : requestedCategories()) {
                message.append(category.label).append("\n").append(category.consequence).append("\n\n");
            }
            message.append("The browser returns to the local KixKan Home page after the request finishes. ")
                    .append("Current tab & history means this session only; this prototype has no separate saved visit-history database.\n\n")
                    .append("Bookmarks, the native KixKan account/session, saved passwords and downloaded files are not selected or erased by this control. ")
                    .append("This is not secure erasure. Gecko reports request completion, not an independent verification of every stored item.");
            return message.toString();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Selection)) return false;
            Selection that = (Selection) other;
            return history == that.history && cache == that.cache && siteData == that.siteData;
        }
        @Override public int hashCode() { return Objects.hash(history, cache, siteData); }
    }

    /** API completion only: Gecko159 does not expose individual cleaner failure bits. */
    public static final class Outcome {
        public final Selection selection;
        public final Set<Category> requestedCategories;
        public final long storageFlags;
        public final Verification verification = Verification.ENGINE_COMPLETION_ONLY;
        public final boolean independentlyVerified = false;

        private Outcome(Selection selection) {
            selection.validate();
            this.selection = selection;
            this.requestedCategories = selection.requestedCategories();
            this.storageFlags = selection.storageFlags();
        }
        static Outcome requestCompleted(Selection selection) { return new Outcome(selection); }
        public String completionText() {
            return selection.cache || selection.siteData
                    ? "Gecko finished processing the selected clear request. The current tab and session history were closed. Complete deletion has not been independently verified; this is not secure erasure."
                    : "The current tab and its back/forward session history were closed. No browser-storage clear request was made.";
        }
    }

    /** Fixed safe messages only: underlying engine errors/URLs are not retained here. */
    public static final class ClearFailure extends RuntimeException {
        public final FailureStage stage;
        public final Selection selection;
        public final boolean sessionBoundaryCompleted;

        ClearFailure(FailureStage stage, Selection selection, boolean sessionBoundaryCompleted) {
            super(failureMessage(stage, selection));
            this.stage = Objects.requireNonNull(stage);
            this.selection = selection;
            this.sessionBoundaryCompleted = sessionBoundaryCompleted;
        }
    }

    private static String failureMessage(FailureStage stage, Selection selection) {
        switch (stage) {
            case VALIDATION:
                return selection != null && selection.validationMessage() != null
                        ? selection.validationMessage() : "The clear request is not valid.";
            case BUSY:
                return "A browsing-data request is already running. Wait for it to finish before trying again.";
            case CLOSE_SESSIONS:
                return "Open pages could not be closed safely. Some tab or history state may already be gone; browser storage was not cleared.";
            case ENGINE_CLEAR:
                return "Gecko could not confirm completion of the selected clear request. The current tab and history were closed; some storage may already have changed. No automatic retry was started.";
            default: throw new IllegalArgumentException("Unsupported clear stage");
        }
    }
}
