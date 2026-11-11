package kr.sbkwon.decolock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local notification history — never leaves the phone. */
public class HistoryDb extends SQLiteOpenHelper {
    private static HistoryDb inst;

    public static synchronized HistoryDb get(Context c) {
        if (inst == null) inst = new HistoryDb(c.getApplicationContext());
        return inst;
    }

    private HistoryDb(Context c) { super(c, "history.db", null, 1); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE h(id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT, app TEXT, title TEXT, body TEXT, ts INTEGER)");
        db.execSQL("CREATE INDEX h_ts ON h(ts)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}

    public static final class Item {
        public long id, ts;
        public String pkg, app, title, body;
    }

    public void add(String pkg, String app, String title, String body, long ts) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("pkg", pkg); v.put("app", app); v.put("title", title); v.put("body", body); v.put("ts", ts);
            db.insert("h", null, v);
            // keep 30 days
            db.delete("h", "ts < ?", new String[]{String.valueOf(System.currentTimeMillis() - 30L * 86400_000L)});
        } catch (RuntimeException ignored) {}
    }

    public List<Item> query(String search, String pkg, int limit) {
        List<Item> out = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND (title LIKE ? OR body LIKE ? OR app LIKE ?)");
            String s = "%" + search.trim() + "%";
            args.add(s); args.add(s); args.add(s);
        }
        if (pkg != null) { where.append(" AND pkg = ?"); args.add(pkg); }
        try (Cursor c = getReadableDatabase().query("h", null, where.toString(), args.toArray(new String[0]),
                null, null, "ts DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                Item i = new Item();
                i.id = c.getLong(0); i.pkg = c.getString(1); i.app = c.getString(2);
                i.title = c.getString(3); i.body = c.getString(4); i.ts = c.getLong(5);
                out.add(i);
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    public void clear() {
        try { getWritableDatabase().delete("h", null, null); } catch (RuntimeException ignored) {}
    }

    public void delete(long id) {
        try { getWritableDatabase().delete("h", "id = ?", new String[]{String.valueOf(id)}); } catch (RuntimeException ignored) {}
    }
}
