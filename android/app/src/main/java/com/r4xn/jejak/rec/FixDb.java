package com.r4xn.jejak.rec;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import com.r4xn.jejak.core.Fix;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Every reading, kept on the phone. Positions are stored as whole numbers (ten-millionths of a degree, tenths of
 * a metre, centimetres per second): about 30 bytes a reading, roughly 2.5 MB for a day of one reading a second.
 */
public final class FixDb extends SQLiteOpenHelper {
    public static final String NAME = "readings.db";
    private final Context context;

    public FixDb(Context c) {
        super(c, NAME, null, 1);
        context = c.getApplicationContext();
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE fixes (t INTEGER PRIMARY KEY, lat INTEGER NOT NULL, lon INTEGER NOT NULL, alt INTEGER, acc INTEGER, spd INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // only one version so far
    }

    public void add(List<Fix> fixes) {
        if (fixes.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (SQLiteStatement st = db.compileStatement("INSERT OR IGNORE INTO fixes (t, lat, lon, alt, acc, spd) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Fix f : fixes) {
                st.clearBindings();
                st.bindLong(1, f.t);
                st.bindLong(2, f.latE7());
                st.bindLong(3, f.lonE7());
                bind(st, 4, Fix.scaled(f.alt, 10));
                bind(st, 5, Fix.scaled(f.acc, 10));
                bind(st, 6, Fix.scaled(f.speed, 100));
                st.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void bind(SQLiteStatement st, int i, Integer v) {
        if (v == null) st.bindNull(i);
        else st.bindLong(i, v);
    }

    /** Readings from {@code from} (inclusive) to {@code to} (exclusive), oldest first. */
    public List<Fix> range(long from, long to) {
        List<Fix> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT t, lat, lon, alt, acc, spd FROM fixes WHERE t >= ? AND t < ? ORDER BY t",
                new String[]{Long.toString(from), Long.toString(to)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    /** The newest {@code n} readings before {@code to}, newest first. */
    public List<Fix> latest(int n, long to) {
        List<Fix> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT t, lat, lon, alt, acc, spd FROM fixes WHERE t < ? ORDER BY t DESC LIMIT " + n,
                new String[]{Long.toString(to)})) {
            while (c.moveToNext()) out.add(read(c));
        }
        return out;
    }

    private static Fix read(Cursor c) {
        return Fix.stored(c.getLong(0), c.getInt(1), c.getInt(2),
                c.isNull(3) ? null : c.getInt(3), c.isNull(4) ? null : c.getInt(4), c.isNull(5) ? null : c.getInt(5));
    }

    public long count(long from, long to) {
        return DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COUNT(*) FROM fixes WHERE t >= ? AND t < ?",
                new String[]{Long.toString(from), Long.toString(to)});
    }

    public long total() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "fixes");
    }

    /** Time of the oldest reading, or 0. */
    public long oldest() {
        return DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COALESCE(MIN(t), 0) FROM fixes", null);
    }

    public int deleteRange(long from, long to) {
        return getWritableDatabase().delete("fixes", "t >= ? AND t < ?", new String[]{Long.toString(from), Long.toString(to)});
    }

    public int deleteBefore(long t) {
        return getWritableDatabase().delete("fixes", "t < ?", new String[]{Long.toString(t)});
    }

    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("fixes", null, null);
        db.execSQL("VACUUM");
    }

    /** Space the readings take on the phone. */
    public long bytes() {
        File f = context.getDatabasePath(NAME);
        long b = f.length();
        File wal = new File(f.getPath() + "-wal");
        if (wal.exists()) b += wal.length();
        return b;
    }
}
