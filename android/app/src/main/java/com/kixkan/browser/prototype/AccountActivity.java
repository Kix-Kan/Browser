package com.kixkan.browser.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean verificationCheckRunning;
    private LinearLayout form;
    private EditText email;
    private EditText password;
    private Button signIn;
    private Button signOut;
    private ProgressBar progress;
    private TextView summary;
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        SystemBars.apply(findViewById(R.id.account_root));
        form = findViewById(R.id.login_form);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        signIn = findViewById(R.id.sign_in);
        signOut = findViewById(R.id.sign_out);
        progress = findViewById(R.id.account_progress);
        summary = findViewById(R.id.account_summary);
        status = findViewById(R.id.account_status);
        signIn.setOnClickListener(view -> signIn());
        signOut.setOnClickListener(view -> signOut());
        findViewById(R.id.manage_account).setOnClickListener(view -> {
            Intent intent = new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_URL, "https://kixkan.com/account")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        render();
    }

    private void render() {
        AccountSession.Value session = AccountSession.load(this);
        boolean signedIn = session != null;
        form.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        signOut.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        summary.setText(signedIn ? "Signed in as " + session.displayName + " (" + session.email + ")" : getString(R.string.account_explanation));
    }

    private void signIn() {
        String enteredEmail = email.getText().toString().trim();
        String enteredPassword = password.getText().toString();
        if (enteredEmail.isEmpty() || enteredPassword.isEmpty()) { status.setText("Enter your email and password."); return; }
        setBusy(true);
        executor.execute(() -> {
            try {
                KixKanApiClient.LoginResult result = KixKanApiClient.login(enteredEmail, enteredPassword);
                AccountSession.save(this, result.token, result.email, result.displayName, result.expiresAt);
                String message = "Signed in. Bookmark sync is active.";
                try {
                    BookmarkSync.run(this);
                } catch (Exception ignored) {
                    message = "Signed in. Bookmark sync will retry when a connection is available.";
                }
                String finalMessage = message;
                runOnUiThread(() -> { password.getText().clear(); setBusy(false); status.setText(finalMessage); render(); refreshVerification(); });
            } catch (Exception error) {
                runOnUiThread(() -> { setBusy(false); status.setText(error.getMessage() == null ? "Sign-in failed." : error.getMessage()); });
            }
        });
    }

    private void signOut() {
        AccountSession.Value session = AccountSession.load(this);
        AccountSession.clear(this);
        render();
        status.setText("Signed out. Local bookmarks remain on this device.");
        if (session != null) executor.execute(() -> { try { KixKanApiClient.logout(session.token); } catch (Exception ignored) {} });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        signIn.setEnabled(!busy);
    }

    private void refreshVerification() {
        AccountSession.Value session = AccountSession.load(this);
        if (session == null || verificationCheckRunning) return;
        String noticeKey = "verified:" + session.email.trim().toLowerCase(java.util.Locale.ROOT);
        if (getSharedPreferences("kixkan_account_notices", MODE_PRIVATE).getBoolean(noticeKey, false)) return;
        verificationCheckRunning = true;
        executor.execute(() -> {
            try {
                boolean verified = KixKanApiClient.emailVerified(session.token);
                if (verified) {
                    getSharedPreferences("kixkan_account_notices", MODE_PRIVATE).edit().putBoolean(noticeKey, true).apply();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        status.setText(R.string.account_verified_message);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.account_verified_title)
                                .setMessage(R.string.account_verified_message)
                                .setPositiveButton(R.string.continue_label, null)
                                .show();
                    });
                }
            } catch (Exception error) {
                if (error instanceof KixKanApiClient.ApiException
                        && ((KixKanApiClient.ApiException) error).status == 401) {
                    AccountSession.clear(this);
                    runOnUiThread(this::render);
                }
            } finally {
                verificationCheckRunning = false;
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        render();
        refreshVerification();
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
