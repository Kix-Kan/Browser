package com.kixkan.browser.prototype;

import android.os.Handler;
import android.os.Looper;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.StorageController;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Runtime-scoped operation coordinator. Owns no Activity, View, file path or account data. */
public final class BrowsingDataController {
    private static final Map<GeckoRuntime, OperationState> STATES = new WeakHashMap<>();
    private static final GeckoResult.CancellationDelegate NO_CANCELLATION = new GeckoResult.CancellationDelegate() {
        @Override public GeckoResult<Boolean> cancel() { return GeckoResult.fromValue(false); }
    };
    private final GeckoRuntime runtime;
    private final OperationState state;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final class OperationState {
        boolean busy;
        GeckoResult<BrowsingData.Outcome> result;
        BrowsingData.Selection selection;
    }

    public BrowsingDataController(GeckoRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        synchronized (STATES) {
            OperationState existing = STATES.get(runtime);
            if (existing == null) { existing = new OperationState(); STATES.put(runtime, existing); }
            state = existing;
        }
    }

    public boolean isBusy() { synchronized (state) { return state.busy; } }
    /** Null before the first accepted request; retains the last terminal result until replaced. */
    public GeckoResult<BrowsingData.Outcome> getInFlight() { synchronized (state) { return state.result; } }
    public BrowsingData.Selection getInFlightSelection() { synchronized (state) { return state.selection; } }

    /**
     * Call on the Android main thread after explicit confirmation. closeSessions
     * MUST synchronously release/close ALL sessions owned by this browser runtime,
     * and throw if it cannot establish that boundary. It is invoked once and not
     * retained. No new session may open until this result completes. The host then
     * opens a fresh local Home page; never restore a full storage-bearing SessionState.
     * No timeout, cancellation rollback or automatic retry is implied/supported.
     */
    public GeckoResult<BrowsingData.Outcome> clear(BrowsingData.Selection selection, Runnable closeSessions) {
        if (Looper.myLooper() != Looper.getMainLooper() || selection == null || !selection.isValid()) {
            return failed(BrowsingData.FailureStage.VALIDATION, selection, false);
        }
        if (closeSessions == null) return failed(BrowsingData.FailureStage.CLOSE_SESSIONS, selection, false);

        final GeckoResult<BrowsingData.Outcome> operation = new GeckoResult<>(mainHandler);
        // Clearing is destructive and the storage API offers no rollback/cancel.
        operation.setCancellationDelegate(NO_CANCELLATION);
        synchronized (state) {
            if (state.busy) return failed(BrowsingData.FailureStage.BUSY, selection, false);
            state.busy = true;
            state.selection = selection;
            state.result = operation;
        }

        boolean boundaryCompleted = false;
        try {
            closeSessions.run();
            boundaryCompleted = true;
            long flags = engineFlags(selection);
            if (flags == 0L) {
                complete(operation, selection);
            } else {
                GeckoResult<Void> clearing = runtime.getStorageController().clearData(flags);
                if (clearing == null) throw new IllegalStateException("Missing engine result");
                clearing.withHandler(mainHandler).accept(
                        ignored -> complete(operation, selection),
                        error -> fail(operation, BrowsingData.FailureStage.ENGINE_CLEAR, selection, true));
            }
        } catch (RuntimeException error) {
            fail(operation, boundaryCompleted ? BrowsingData.FailureStage.ENGINE_CLEAR
                    : BrowsingData.FailureStage.CLOSE_SESSIONS, selection, boundaryCompleted);
        }
        return operation;
    }

    /** Only the four explicitly selected engine flags, never SITE_DATA/ALL/settings/auth. */
    static long engineFlags(BrowsingData.Selection selection) {
        selection.validate();
        long flags = 0;
        if (selection.cache) flags |= StorageController.ClearFlags.NETWORK_CACHE | StorageController.ClearFlags.IMAGE_CACHE;
        if (selection.siteData) flags |= StorageController.ClearFlags.COOKIES | StorageController.ClearFlags.DOM_STORAGES;
        if (flags != selection.storageFlags()) throw new IllegalStateException("Unsupported engine clear mapping");
        return flags;
    }

    private GeckoResult<BrowsingData.Outcome> failed(BrowsingData.FailureStage stage,
            BrowsingData.Selection selection, boolean boundaryCompleted) {
        GeckoResult<BrowsingData.Outcome> result = new GeckoResult<>(mainHandler);
        result.completeExceptionally(new BrowsingData.ClearFailure(stage, selection, boundaryCompleted));
        return result;
    }

    private void complete(GeckoResult<BrowsingData.Outcome> operation, BrowsingData.Selection selection) {
        synchronized (state) {
            if (state.result != operation || !state.busy) return;
            state.busy = false;
        }
        operation.complete(BrowsingData.Outcome.requestCompleted(selection));
    }

    private void fail(GeckoResult<BrowsingData.Outcome> operation, BrowsingData.FailureStage stage,
            BrowsingData.Selection selection, boolean boundaryCompleted) {
        synchronized (state) {
            if (state.result != operation || !state.busy) return;
            state.busy = false;
        }
        operation.completeExceptionally(new BrowsingData.ClearFailure(stage, selection, boundaryCompleted));
    }
}
