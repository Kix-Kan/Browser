package com.kixkan.browser.prototype;

import android.content.Context;

import org.json.JSONObject;

import java.util.List;

final class BookmarkSync {
    private BookmarkSync() {}

    static int run(Context context) throws Exception {
        AccountSession.Value session = AccountSession.load(context);
        if (session == null) return 0;
        BookmarkStore store = new BookmarkStore(context);
        int changed = 0;
        try {
            List<BookmarkStore.Entry> pending = store.pending();
            if (!pending.isEmpty()) {
                JSONObject pushed = KixKanApiClient.pushBookmarks(session.token, pending);
                store.markPushed(pending, pushed.getLong("cursor"));
                changed += pending.size();
            }
            boolean more;
            int pages = 0;
            do {
                JSONObject pulled = KixKanApiClient.pullBookmarks(session.token, store.cursor());
                store.applyRemote(pulled.getJSONArray("bookmarks"), pulled.getLong("cursor"));
                changed += pulled.getJSONArray("bookmarks").length();
                more = pulled.optBoolean("hasMore");
                pages++;
            } while (more && pages < 10);
            return changed;
        } catch (KixKanApiClient.ApiException error) {
            if (error.status == 401) AccountSession.clear(context);
            throw error;
        } finally {
            store.close();
        }
    }
}
