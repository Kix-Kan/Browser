package com.kixkan.browser.prototype;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BookmarkStore extends SQLiteOpenHelper {
    static final class Entry {
        final String id;
        final String title;
        final String url;
        final String folder;
        final int position;
        final boolean deleted;

        Entry(String id, String title, String url, String folder, int position, boolean deleted) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.folder = folder;
            this.position = position;
            this.deleted = deleted;
        }

        @Override public String toString() { return title + "\n" + url; }
    }

    BookmarkStore(Context context) {
        super(context.getApplicationContext(), "kixkan-bookmarks.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE bookmarks (id TEXT PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, folder TEXT NOT NULL DEFAULT '', position INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, revision INTEGER NOT NULL DEFAULT 0)");
        database.execSQL("CREATE TABLE sync_state (singleton INTEGER PRIMARY KEY CHECK(singleton = 1), cursor INTEGER NOT NULL DEFAULT 0)");
        database.execSQL("INSERT INTO sync_state(singleton, cursor) VALUES(1, 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {}

    synchronized void add(String title, String url) {
        ContentValues values = new ContentValues();
        values.put("id", UUID.randomUUID().toString());
        values.put("title", title == null || title.trim().isEmpty() ? url : title.trim());
        values.put("url", url);
        values.put("folder", "");
        values.put("position", active().size());
        values.put("deleted", 0);
        values.put("dirty", 1);
        values.put("revision", 0);
        getWritableDatabase().insertOrThrow("bookmarks", null, values);
    }

    synchronized void remove(String id) {
        ContentValues values = new ContentValues();
        values.put("deleted", 1);
        values.put("dirty", 1);
        getWritableDatabase().update("bookmarks", values, "id = ?", new String[]{id});
    }

    synchronized List<Entry> active() { return select("deleted = 0", null); }
    synchronized List<Entry> pending() { return select("dirty = 1", null); }

    synchronized long cursor() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT cursor FROM sync_state WHERE singleton = 1", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    synchronized void markPushed(List<Entry> entries, long revision) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (Entry entry : entries) {
                ContentValues values = new ContentValues();
                values.put("dirty", 0);
                values.put("revision", revision);
                database.update("bookmarks", values, "id = ?", new String[]{entry.id});
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    synchronized void applyRemote(JSONArray changes, long newCursor) throws Exception {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (int index = 0; index < changes.length(); index++) {
                JSONObject item = changes.getJSONObject(index);
                String id = item.getString("id");
                if (isDirty(database, id)) continue;
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("title", item.optString("title"));
                values.put("url", item.optString("url"));
                values.put("folder", item.optString("folder"));
                values.put("position", item.optInt("position"));
                values.put("deleted", item.optBoolean("deleted") ? 1 : 0);
                values.put("dirty", 0);
                values.put("revision", item.getLong("revision"));
                database.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues state = new ContentValues();
            state.put("cursor", newCursor);
            database.update("sync_state", state, "singleton = 1", null);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    private boolean isDirty(SQLiteDatabase database, String id) {
        try (Cursor cursor = database.rawQuery("SELECT dirty FROM bookmarks WHERE id = ?", new String[]{id})) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private List<Entry> select(String where, String[] arguments) {
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("bookmarks", new String[]{"id", "title", "url", "folder", "position", "deleted"}, where, arguments, null, null, "position ASC, title COLLATE NOCASE ASC", "100")) {
            while (cursor.moveToNext()) entries.add(new Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4), cursor.getInt(5) == 1));
        }
        return entries;
    }
}
