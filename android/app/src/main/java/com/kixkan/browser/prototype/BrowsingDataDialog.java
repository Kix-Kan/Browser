package com.kixkan.browser.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Activity-owned native controls. Merely opening, changing or cancelling never clears data. */
final class BrowsingDataDialog {
    interface Confirmed { void clear(BrowsingData.Selection selection); }
    private final Activity activity;
    private AlertDialog choices;
    private AlertDialog confirmation;

    BrowsingDataDialog(Activity activity) { this.activity = activity; }

    void show(Confirmed confirmed) {
        if (choices != null && choices.isShowing()) return;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(16));
        TextView heading = text("Your browsing.\nYour choice.", 25, true);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        content.addView(heading);
        content.addView(text("ALL TIME", 12, true));
        content.addView(text("Choose what this browser should forget. Nothing changes until you confirm.", 15, false));
        CheckBox history = option(content, R.id.privacy_history, "Current tab & history",
                "Close the page and forget Back/Forward navigation. This prototype has no separate saved-history list.");
        CheckBox cache = option(content, R.id.privacy_cache, "Cached images and files",
                "Request removal of network and image caches. Websites may load more slowly on your next visit.");
        CheckBox sites = option(content, R.id.privacy_sites, "Cookies and site data",
                "Request removal of cookies and website storage. Websites may sign you out.");
        TextView status = text("", 14, false);
        status.setId(R.id.privacy_selection_status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        content.addView(text("To stop open pages from restoring data, also select Current tab & history when clearing caches or site data.", 14, false));
        content.addView(text("Kept: bookmarks, KixKan account, downloaded files and site permissions. No secure-disk-erasure claim.", 14, false));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(content);
        choices = new AlertDialog.Builder(activity)
                .setTitle("Browsing data")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Review", null)
                .create();
        choices.setOnShowListener(ignored -> {
            choices.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.privacy_review);
            choices.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
            choices.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
            Runnable update = () -> {
                BrowsingData.Selection selection = new BrowsingData.Selection(
                        history.isChecked(), cache.isChecked(), sites.isChecked());
                choices.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selection.isValid());
                status.setText(selection.isValid() ? "Ready to review. No data has been changed." : selection.validationMessage());
            };
            for (CheckBox box : new CheckBox[]{history, cache, sites}) {
                box.setOnCheckedChangeListener((button, checked) -> update.run());
            }
            choices.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                BrowsingData.Selection selection = new BrowsingData.Selection(
                        history.isChecked(), cache.isChecked(), sites.isChecked());
                if (!selection.isValid()) return;
                confirmation = new AlertDialog.Builder(activity)
                        .setTitle("Clear selected browsing data?")
                        .setMessage(selection.confirmationText()
                                + "\n\nThis cannot be undone. Bookmarks and your KixKan account are kept."
                                + "\n\nOnly this browser is affected. This is not secure disk erasure.")
                        .setNegativeButton("Keep data", null)
                        .setPositiveButton("Clear selected data", (dialog, which) -> {
                            dismiss();
                            confirmed.clear(selection);
                        }).create();
                confirmation.setOnShowListener(dialog -> {
                    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setId(R.id.privacy_confirm_clear);
                    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
                    confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
                });
                confirmation.show();
            });
            update.run();
        });
        choices.show();
    }

    void dismiss() {
        if (confirmation != null) { confirmation.dismiss(); confirmation = null; }
        if (choices != null) { choices.dismiss(); choices = null; }
    }

    private CheckBox option(LinearLayout parent, int id, String title, String detail) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(4), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(activity.getColor(R.color.kixkan_panel));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), activity.getColor(R.color.kixkan_border));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        parent.addView(card, params);
        CheckBox checkbox = new CheckBox(activity);
        checkbox.setId(id);
        checkbox.setText(title);
        checkbox.setTextSize(16);
        checkbox.setTextColor(activity.getColor(R.color.kixkan_ink));
        checkbox.setMinHeight(dp(52));
        card.addView(checkbox, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(detail, 14, false);
        description.setPadding(dp(36), 0, 0, 0);
        card.addView(description);
        return checkbox;
    }

    private TextView text(String value, int size, boolean strong) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(activity.getColor(strong ? R.color.kixkan_ink : R.color.kixkan_muted));
        if (strong) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
