package com.kixkan.browser.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.GeckoResult;

import java.util.List;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    static final String EXTRA_URL = "com.kixkan.browser.URL";
    private static final int REQUEST_BROWSER_ROLE = 0x4B42;
    private static GeckoRuntime runtime;
    private static BrowsingDataController dataController;
    private static GeckoResult<BrowsingData.Outcome> pendingUiClear;
    private GeckoResult<BrowsingData.Outcome> observedClear;
    private BrowsingDataDialog dataChoices;
    private AlertDialog dataProgress;
    private AlertDialog dataResult;

    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private GeckoSession session;
    private GeckoView geckoView;
    private BrowserSessionCallbacks sessionCallbacks;
    private boolean sessionTransferred;
    private BookmarkStore bookmarkStore;
    private EditText address;
    private Button back;
    private Button forward;
    private ProgressBar progress;
    private ImageButton account;
    private String currentUrl = "";
    private String currentTitle = "";

    /** No strong Activity, View, Context or resource reference enters this holder. */
    private static final class RetainedBrowserSession {
        final GeckoSession session;
        final BrowserSessionCallbacks callbacks;
        final String editorText;
        final boolean editing;
        final int selectionStart;
        final int selectionEnd;
        final long locationGeneration;

        RetainedBrowserSession(GeckoSession session, BrowserSessionCallbacks callbacks,
                String editorText, boolean editing, int selectionStart, int selectionEnd) {
            this.session = session;
            this.callbacks = callbacks;
            this.editorText = editorText;
            this.editing = editing;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.locationGeneration = callbacks.locationGeneration;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBars.apply(findViewById(R.id.browser_root));

        geckoView = findViewById(R.id.gecko_view);
        geckoView.setBackgroundColor(getColor(R.color.kixkan_night));
        address = findViewById(R.id.address);
        // The session model (or an explicit retained editor draft) owns this
        // field. Framework EditText state must not replace the displayed URL.
        address.setSaveEnabled(false);
        back = findViewById(R.id.back);
        forward = findViewById(R.id.forward);
        progress = findViewById(R.id.progress);
        account = findViewById(R.id.account);
        bookmarkStore = new BookmarkStore(this);

        if (runtime == null) {
            runtime = GeckoRuntime.create(getApplicationContext(), buildRuntimeSettings());
        }
        applyRuntimeAppearance(getResources().getConfiguration());
        if (dataController == null) dataController = new BrowsingDataController(runtime);
        GeckoResult<BrowsingData.Outcome> pendingClear = pendingUiClear;
        boolean waitingForClear = pendingClear != null;

        Object previous = getLastNonConfigurationInstance();
        RetainedBrowserSession retained = previous instanceof RetainedBrowserSession
                ? (RetainedBrowserSession) previous : null;
        boolean restoringSession = retained != null && retained.session.isOpen();
        if (waitingForClear) {
            // Never open a site session while an engine clear is still pending.
            session = null;
            sessionCallbacks = null;
        } else if (restoringSession) {
            session = retained.session;
            sessionCallbacks = retained.callbacks;
            // This document has already painted. A new opaque first-paint cover
            // could remain forever because no new document load is requested.
            geckoView.coverUntilFirstPaint(Color.TRANSPARENT);
        } else {
            session = new GeckoSession();
            sessionCallbacks = new BrowserSessionCallbacks();
            session.setContentDelegate(sessionCallbacks);
            session.setNavigationDelegate(sessionCallbacks);
            session.setProgressDelegate(sessionCallbacks);
            geckoView.coverUntilFirstPaint(getColor(R.color.kixkan_night));
        }
        // Keep the session-owned engine observers registered across recreation.
        // Nulling/replacing these toggles Gecko navigation modules off/on.
        if (!waitingForClear) {
            sessionCallbacks.attach(this);
            if (!restoringSession) session.open(runtime);
            geckoView.setSession(session);
        }
        if (restoringSession && retained.editing
                && retained.locationGeneration == sessionCallbacks.locationGeneration) {
            address.setText(retained.editorText);
            int length = retained.editorText.length();
            address.setSelection(Math.max(0, Math.min(length, retained.selectionStart)),
                    Math.max(0, Math.min(length, retained.selectionEnd)));
            address.requestFocus();
        }

        back.setOnClickListener(view -> session.goBack());
        forward.setOnClickListener(view -> session.goForward());
        address.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                navigateFromAddress();
                return true;
            }
            return false;
        });
        findViewById(R.id.home).setOnClickListener(view -> session.loadUri(NavigationInput.START_URI));
        findViewById(R.id.save_bookmark).setOnClickListener(view -> saveBookmark());
        findViewById(R.id.bookmarks).setOnClickListener(view -> startActivity(new Intent(this, BookmarksActivity.class)));
        account.setOnClickListener(view -> startActivity(new Intent(this, AccountActivity.class)));
        findViewById(R.id.menu).setOnClickListener(this::showMenu);

        if (waitingForClear) {
            setBrowsingEnabled(false);
            watchClear(pendingClear);
        } else if (!restoringSession) {
            String requestedUrl = incomingWebUrl(getIntent());
            session.loadUri(requestedUrl == null ? NavigationInput.START_URI : requestedUrl);
        }
    }

    private static GeckoRuntimeSettings buildRuntimeSettings() {
        ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                .strictSocialTrackingProtection(true)
                .cookiePurging(true)
                .queryParameterStrippingEnabled(true)
                .queryParameterStrippingPrivateBrowsingEnabled(true)
                .contentBlockingDatabase(false)
                .safeBrowsingRealTimeEnabled(false)
                .build();

        return new GeckoRuntimeSettings.Builder()
                .contentBlocking(blocking)
                .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
                .globalPrivacyControlEnabled(true)
                .aboutConfigEnabled(false)
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .loginAutofillEnabled(false)
                .automaticFontSizeAdjustment(true)
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .build();
    }

    static int webColorScheme(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ? GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;
    }

    private void applyRuntimeAppearance(Configuration configuration) {
        // Mirror Android's actual appearance; sites retain control of their CSS.
        // Do not rely on an application-context SYSTEM preference being refreshed.
        if (runtime != null) runtime.getSettings().setPreferredColorScheme(webColorScheme(configuration));
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyRuntimeAppearance(configuration);
    }

    private void navigateFromAddress() {
        session.loadUri(NavigationInput.resolve(address.getText().toString()));
        address.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(address.getWindowToken(), 0);
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(R.string.reload).setOnMenuItemClickListener(item -> {
            session.reload();
            return true;
        });
        popup.getMenu().add(R.string.home).setOnMenuItemClickListener(item -> {
            session.loadUri(NavigationInput.START_URI);
            return true;
        });
        popup.getMenu().add(R.string.save_bookmark).setOnMenuItemClickListener(item -> {
            saveBookmark();
            return true;
        });
        popup.getMenu().add(R.string.bookmarks).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, BookmarksActivity.class));
            return true;
        });
        popup.getMenu().add(R.string.account_settings).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, AccountActivity.class));
            return true;
        });
        popup.getMenu().add("Browsing data").setOnMenuItemClickListener(item -> {
            if (dataChoices == null) dataChoices = new BrowsingDataDialog(this);
            dataChoices.show(this::clearBrowsingData);
            return true;
        });
        popup.getMenu().add(R.string.about_browser).setOnMenuItemClickListener(item -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage("Developer prototype " + BuildConfig.VERSION_NAME
                            + "\n\nBuilt on Mozilla GeckoView. HTTPS-only navigation, Global Privacy Control and tracker protection are enabled."
                            + "\n\nSelective all-time browsing-data controls are available from the menu. Downloads and multiple tabs are not implemented yet. No account is required to browse."
                            + "\n\nDeveloped by Broadway Pixels. Browser updates require a new tested package; no production updater is configured.")
                    .setPositiveButton(android.R.string.ok, null).show();
            return true;
        });
        popup.getMenu().add(R.string.set_default_browser).setOnMenuItemClickListener(item -> {
            requestDefaultBrowser();
            return true;
        });
        popup.show();
    }

    /** User-initiated only: Android owns the consent and resulting role choice. */
    @SuppressWarnings("deprecation")
    private void requestDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = getSystemService(RoleManager.class);
                if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    Toast.makeText(this, R.string.default_browser_unavailable, Toast.LENGTH_LONG).show();
                } else if (roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    Toast.makeText(this, R.string.browser_already_default, Toast.LENGTH_SHORT).show();
                } else {
                    // Android needs a for-result caller identity for role consent.
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER), REQUEST_BROWSER_ROLE);
                }
            } else {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            }
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, R.string.default_browser_error, Toast.LENGTH_LONG).show();
        }
    }

    private void saveBookmark() {
        if (!currentUrl.startsWith("https://") && !currentUrl.startsWith("http://")) {
            Toast.makeText(this, "Only web pages can be bookmarked.", Toast.LENGTH_SHORT).show();
            return;
        }
        bookmarkStore.add(currentTitle, currentUrl);
        Toast.makeText(this, AccountSession.load(this) == null ? "Bookmark saved locally" : "Bookmark saved · Syncing", Toast.LENGTH_SHORT).show();
        synchronizeBookmarks();
    }

    private void clearBrowsingData(BrowsingData.Selection selection) {
        if (!selection.isValid() || dataController.isBusy()) return;
        setBrowsingEnabled(false);
        GeckoResult<BrowsingData.Outcome> operation = dataController.clear(selection, this::closeSessionForClear);
        pendingUiClear = operation;
        watchClear(operation);
    }

    private void closeSessionForClear() {
        if (geckoView.getSession() != null) geckoView.releaseSession();
        if (sessionCallbacks != null) sessionCallbacks.detach(this);
        if (session != null) {
            clearSessionDelegates();
            session.close();
        }
        session = null;
        sessionCallbacks = null;
        currentUrl = "";
        currentTitle = "";
        address.setText("");
    }

    private void setBrowsingEnabled(boolean enabled) {
        for (int id : new int[]{R.id.address, R.id.home, R.id.save_bookmark,
                R.id.bookmarks, R.id.account, R.id.menu}) {
            findViewById(id).setEnabled(enabled);
        }
        if (!enabled) { back.setEnabled(false); forward.setEnabled(false); }
    }

    private void watchClear(GeckoResult<BrowsingData.Outcome> operation) {
        if (operation == observedClear) return;
        observedClear = operation;
        dataProgress = new AlertDialog.Builder(this)
                .setTitle("Clearing browsing data")
                .setMessage("Waiting for the browser engine. Browsing is paused so websites cannot restore data. This operation cannot be undone or cancelled after confirmation.")
                .setCancelable(false).create();
        dataProgress.show();
        WeakReference<MainActivity> owner = new WeakReference<>(this);
        operation.then(outcome -> {
            finishClear(owner, operation, outcome, null);
            return null;
        }, failure -> {
            finishClear(owner, operation, null, failure);
            return null;
        });
    }

    private static void finishClear(WeakReference<MainActivity> owner,
            GeckoResult<BrowsingData.Outcome> operation, BrowsingData.Outcome outcome, Throwable failure) {
        MainActivity activity = owner.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (pendingUiClear != operation) return;
        pendingUiClear = null;
        if (activity.dataProgress != null) {
            activity.dataProgress.dismiss();
            activity.dataProgress = null;
        }
        try {
            activity.openFreshStartAfterClear();
        } catch (RuntimeException restartFailure) {
            activity.setBrowsingEnabled(false);
            activity.dataResult = new AlertDialog.Builder(activity)
                    .setTitle("Browser could not reopen")
                    .setMessage("The clear request has finished, but a fresh browser session could not open. Close and reopen KixKan Browser. Completion of all selected data removal has not been independently verified.")
                    .setPositiveButton("Close browser", (dialog, which) -> activity.finish()).create();
            activity.dataResult.show();
            return;
        }
        String message = failure == null
                ? outcome.completionText() + "\n\nBookmarks and your KixKan account were kept."
                : "The clear request could not be confirmed. Some selected data may already have been removed. Bookmarks and your KixKan account were not selected. You can review your choices and try again.";
        android.widget.TextView status = new android.widget.TextView(activity);
        status.setId(R.id.privacy_operation_status);
        status.setText(message);
        status.setTextSize(16);
        status.setTextColor(activity.getColor(R.color.kixkan_ink));
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        status.setPadding(padding, padding / 2, padding, padding);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        activity.dataResult = new AlertDialog.Builder(activity)
                .setTitle(failure == null ? "Clear request completed" : "Clear request not confirmed")
                .setView(status).setPositiveButton("Done", null).create();
        activity.dataResult.show();
    }

    private void openFreshStartAfterClear() {
        // No website document or serialized session state is restored.
        closeSessionForClear();
        session = new GeckoSession();
        sessionCallbacks = new BrowserSessionCallbacks();
        session.setContentDelegate(sessionCallbacks);
        session.setNavigationDelegate(sessionCallbacks);
        session.setProgressDelegate(sessionCallbacks);
        sessionCallbacks.attach(this);
        geckoView.coverUntilFirstPaint(getColor(R.color.kixkan_night));
        session.open(runtime);
        geckoView.setSession(session);
        setBrowsingEnabled(true);
        session.loadUri(NavigationInput.START_URI);
    }

    private void synchronizeBookmarks() {
        if (AccountSession.load(this) == null) return;
        syncExecutor.execute(() -> {
            try { BookmarkSync.run(this); }
            catch (Exception error) { runOnUiThread(() -> Toast.makeText(this, "Bookmark sync paused", Toast.LENGTH_SHORT).show()); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        AccountSession.Value signedIn = AccountSession.load(this);
        account.setContentDescription(signedIn == null
                ? getString(R.string.account_settings)
                : getString(R.string.account_settings_signed_in, signedIn.displayName));
        synchronizeBookmarks();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String requestedUrl = incomingWebUrl(intent);
        if (requestedUrl != null && session != null && !dataController.isBusy()) session.loadUri(requestedUrl);
    }

    private String incomingWebUrl(Intent intent) {
        try { return BrowserIntents.requestedHttpUrl(intent); }
        catch (RuntimeException rejected) {
            Toast.makeText(this, R.string.external_link_rejected, Toast.LENGTH_LONG).show();
            return null;
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (session == null || !session.isOpen() || geckoView == null
                || geckoView.getSession() != session) return null;
        GeckoSession detached = geckoView.releaseSession();
        if (detached != session) return null;
        // Drop the weak UI owner, not the observers: disabling/re-enabling a
        // Gecko module can replay bootstrap state over the retained URL.
        sessionCallbacks.detach(this);
        sessionTransferred = true;
        return new RetainedBrowserSession(session, sessionCallbacks,
                address.getText().toString(), address.hasFocus(),
                address.getSelectionStart(), address.getSelectionEnd());
    }

    private void clearSessionDelegates() {
        session.setContentDelegate(null);
        session.setNavigationDelegate(null);
        session.setProgressDelegate(null);
    }

    @Override
    protected void onDestroy() {
        if (dataChoices != null) { dataChoices.dismiss(); dataChoices = null; }
        if (dataProgress != null) { dataProgress.dismiss(); dataProgress = null; }
        if (dataResult != null) { dataResult.dismiss(); dataResult = null; }
        if (geckoView != null && geckoView.getSession() != null) geckoView.releaseSession();
        if (sessionCallbacks != null) sessionCallbacks.detach(this);
        if (session != null && !sessionTransferred) {
            clearSessionDelegates();
            session.close();
        }
        syncExecutor.shutdownNow();
        if (bookmarkStore != null) bookmarkStore.close();
        super.onDestroy();
    }

    /** Session-owned state/observers retain no strong Activity or View reference. */
    private static final class BrowserSessionCallbacks implements GeckoSession.ContentDelegate,
            GeckoSession.NavigationDelegate, GeckoSession.ProgressDelegate {
        private WeakReference<MainActivity> owner = new WeakReference<>(null);
        private String url = "";
        private String title = "";
        private boolean back;
        private boolean forward;
        private boolean loading;
        private long locationGeneration;

        void attach(MainActivity activity) {
            owner = new WeakReference<>(activity);
            activity.currentUrl = url;
            activity.currentTitle = title;
            activity.address.setText(url);
            activity.back.setEnabled(back);
            activity.forward.setEnabled(forward);
            activity.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }

        void detach(MainActivity expected) {
            if (owner.get() == expected) owner.clear();
        }

        @Override public void onTitleChange(GeckoSession changedSession, String title) {
            this.title = title == null ? "" : title;
            MainActivity activity = owner.get();
            if (activity != null) activity.currentTitle = this.title;
        }

        @Override
        public void onLocationChange(
                GeckoSession changedSession,
                String url,
                List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                Boolean hasUserGesture) {
            this.url = url == null || url.startsWith("resource://android/") ? "" : url;
            locationGeneration++;
            MainActivity activity = owner.get();
            if (activity != null) {
                activity.currentUrl = this.url;
                activity.address.setText(this.url);
            }
        }

        @Override
        public void onCanGoBack(GeckoSession changedSession, boolean canGoBack) {
            back = canGoBack;
            MainActivity activity = owner.get();
            if (activity != null) {
                activity.back.setEnabled(canGoBack);
            }
        }

        @Override
        public void onCanGoForward(GeckoSession changedSession, boolean canGoForward) {
            forward = canGoForward;
            MainActivity activity = owner.get();
            if (activity != null) {
                activity.forward.setEnabled(canGoForward);
            }
        }

        @Override
        public void onPageStart(GeckoSession changedSession, String url) {
            loading = true;
            MainActivity activity = owner.get();
            if (activity != null) {
                activity.progress.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onPageStop(GeckoSession changedSession, boolean success) {
            loading = false;
            MainActivity activity = owner.get();
            if (activity != null) {
                activity.progress.setVisibility(View.GONE);
            }
        }
    }
}
