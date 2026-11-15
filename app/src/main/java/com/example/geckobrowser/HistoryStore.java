package com.example.geckobrowser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Browsing history in SQLite. Writes run on a background thread. */
public class HistoryStore extends SQLiteOpenHelper {

    public static final class Entry {
        public final long id;
        public final String url;
        public final String title;
        public final long visited;

        Entry(long id, String url, String title, long visited) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.visited = visited;
        }
    }

    private static final String DB_NAME = "history.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_ENTRIES = 3000;
    private static final long MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000;
    /** Revisiting the same URL within this window updates the entry instead of adding one. */
    private static final long MERGE_WINDOW_MS = 30L * 60 * 1000;

    private static HistoryStore sInstance;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    public static synchronized HistoryStore get(Context context) {
        if (sInstance == null) {
            sInstance = new HistoryStore(context.getApplicationContext());
        }
        return sInstance;
    }

    private HistoryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "url TEXT NOT NULL, title TEXT, visited INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX history_visited ON history (visited DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static boolean isRecordable(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return !(lower.startsWith("about:") || lower.startsWith("data:")
                || lower.startsWith("blob:") || lower.startsWith("javascript:")
                || lower.startsWith("moz-extension:") || lower.startsWith("resource:"));
    }

    public void recordVisit(final String url, final String title) {
        if (!isRecordable(url)) {
            return;
        }
        final long now = System.currentTimeMillis();
        writer.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            long existingId = -1;
            try (Cursor cursor = db.query("history", new String[]{"_id"},
                    "url = ? AND visited > ?", new String[]{url, String.valueOf(now - MERGE_WINDOW_MS)},
                    null, null, "visited DESC", "1")) {
                if (cursor.moveToFirst()) {
                    existingId = cursor.getLong(0);
                }
            }
            ContentValues values = new ContentValues();
            values.put("url", url);
            values.put("visited", now);
            if (title != null && !title.trim().isEmpty()) {
                values.put("title", title.trim());
            }
            if (existingId >= 0) {
                db.update("history", values, "_id = ?", new String[]{String.valueOf(existingId)});
            } else {
                db.insert("history", null, values);
                prune(db, now);
            }
        });
    }

    public void updateTitle(final String url, final String title) {
        if (!isRecordable(url) || title == null || title.trim().isEmpty()) {
            return;
        }
        writer.execute(() -> {
            ContentValues values = new ContentValues();
            values.put("title", title.trim());
            getWritableDatabase().update("history", values,
                    "_id = (SELECT _id FROM history WHERE url = ? ORDER BY visited DESC LIMIT 1)",
                    new String[]{url});
        });
    }

    private void prune(SQLiteDatabase db, long now) {
        db.delete("history", "visited < ?", new String[]{String.valueOf(now - MAX_AGE_MS)});
        db.execSQL("DELETE FROM history WHERE _id NOT IN "
                + "(SELECT _id FROM history ORDER BY visited DESC LIMIT " + MAX_ENTRIES + ")");
    }

    /** Newest first. Reads are small and done on the caller's thread. */
    public List<Entry> recent(int limit) {
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("history",
                new String[]{"_id", "url", "title", "visited"}, null, null, null, null,
                "visited DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getLong(3)));
            }
        }
        return entries;
    }

    /** Pages whose address or title contains {@code text}, most recently visited first. */
    public List<Entry> search(String text, int limit) {
        List<Entry> entries = new ArrayList<>();
        String like = "%" + text.replace("%", "").replace("_", "") + "%";
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT MAX(_id), url, title, MAX(visited) AS last FROM history "
                        + "WHERE url LIKE ? OR title LIKE ? GROUP BY url ORDER BY last DESC LIMIT "
                        + limit, new String[]{like, like})) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getLong(3)));
            }
        }
        return entries;
    }

    /** Most recent known page title for a URL, or null. */
    public String titleFor(String url) {
        if (!isRecordable(url)) {
            return null;
        }
        try (Cursor cursor = getReadableDatabase().query("history", new String[]{"title"},
                "url = ? AND title IS NOT NULL", new String[]{url}, null, null,
                "visited DESC", "1")) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public void delete(final long id) {
        writer.execute(() -> getWritableDatabase().delete("history", "_id = ?",
                new String[]{String.valueOf(id)}));
    }

    public void clear() {
        writer.execute(() -> getWritableDatabase().delete("history", null, null));
    }
}
