package com.kixkan.browser.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BookmarksActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BookmarkStore store;
    private ListView list;
    private TextView status;
    private List<BookmarkStore.Entry> entries;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        SystemBars.apply(findViewById(R.id.bookmarks_root));
        store = new BookmarkStore(this);
        list = findViewById(R.id.bookmark_list);
        status = findViewById(R.id.bookmark_sync_status);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_URL, entries.get(position).url);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            BookmarkStore.Entry entry = entries.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Delete bookmark?")
                    .setMessage(entry.title)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> {
                        store.remove(entry.id);
                        refresh();
                        synchronize();
                    })
                    .show();
            return true;
        });
        refresh();
        synchronize();
    }

    private void refresh() {
        entries = store.active();
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, entries));
        status.setText(AccountSession.load(this) == null ? "Local bookmarks · Sign in to sync" : "Bookmark sync enabled · Long-press to delete");
    }

    private void synchronize() {
        if (AccountSession.load(this) == null) return;
        executor.execute(() -> {
            try { BookmarkSync.run(this); runOnUiThread(() -> { status.setText("Bookmarks synced"); refresh(); }); }
            catch (Exception error) { runOnUiThread(() -> status.setText("Sync paused: " + (error.getMessage() == null ? "network error" : error.getMessage()))); }
        });
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        store.close();
        super.onDestroy();
    }
}
