package kr.sbkwon.offlinedict;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Inflater;

/** Read-only access to the dictionary database built by tools/build_db.py. */
public class DictDb {
    private final SQLiteDatabase db;
    private final LruCache<Integer, String[]> blocks = new LruCache<>(96);

    public DictDb(String path) {
        db = SQLiteDatabase.openDatabase(path, null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    }

    /** MUST match norm() in tools/build_db.py */
    public static String norm(String s) {
        if (s == null) return "";
        s = s.trim().toLowerCase(Locale.ROOT).replace('đ', 'd');
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{Mn}+", "");
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        return s.replaceAll("\\s+", " ");
    }

    private static String upper(String k) {
        // smallest string greater than every string that starts with k
        int last = k.length() - 1;
        return k.substring(0, last) + (char) (k.charAt(last) + 1);
    }

    private static String inList(String csv) {
        StringBuilder sb = new StringBuilder();
        if (csv != null) {
            for (String p : csv.split(",")) {
                p = p.trim();
                if (p.matches("\\d{1,3}")) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(p);
                }
            }
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    public synchronized String dicts() throws Exception {
        JSONArray arr = new JSONArray();
        Cursor c = db.rawQuery("SELECT id,code,src,dst,name,fmt,cnt,ord FROM dicts ORDER BY ord", null);
        try {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("id", c.getInt(0));
                o.put("code", c.getString(1));
                o.put("src", c.getString(2));
                o.put("dst", c.getString(3));
                o.put("name", c.getString(4));
                o.put("fmt", c.getString(5));
                o.put("cnt", c.getInt(6));
                o.put("ord", c.getDouble(7));
                arr.put(o);
            }
        } finally {
            c.close();
        }
        return arr.toString();
    }

    private String[] block(int id) throws Exception {
        String[] b = blocks.get(id);
        if (b != null) return b;
        Cursor c = db.rawQuery("SELECT data FROM blocks WHERE id=?", new String[]{String.valueOf(id)});
        byte[] data;
        try {
            if (!c.moveToFirst()) return new String[0];
            data = c.getBlob(0);
        } finally {
            c.close();
        }
        Inflater inf = new Inflater();
        inf.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
        byte[] buf = new byte[32768];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            out.write(buf, 0, n);
        }
        inf.end();
        b = new String(out.toByteArray(), "UTF-8").split("\u0000", -1);
        blocks.put(id, b);
        return b;
    }

    private String body(int blk, int idx) throws Exception {
        String[] b = block(blk);
        return idx < b.length ? b[idx] : "";
    }

    /** 한국어기초사전 (12) first, otherwise by dictionary id */
    private static int prio(int d) { return d == 12 ? 0 : d; }

    private static class Row {
        String k, hw;
        int d, b, i;
    }

    private static class Group {
        String hw, k;
        List<Row> rows = new ArrayList<>();
        int rank;
    }

    /** Prefix search. Returns [{h, k, d:[ids], p:{d, body}}] grouped by headword. */
    public synchronized String search(String q, String dictsCsv, int max) throws Exception {
        String k = norm(q);
        JSONArray res = new JSONArray();
        if (k.isEmpty()) return res.toString();
        String in = inList(dictsCsv);
        String hi = upper(k);
        String sql = "SELECT * FROM (SELECT k,d,hw,b,i FROM entries WHERE k>=?1 AND k<?2 AND d IN (" + in + ") LIMIT 1500)"
                + " UNION ALL SELECT * FROM (SELECT e.k,e.d,e.hw,e.b,e.i FROM xkeys x JOIN entries e ON e.k=x.tk"
                + " WHERE x.k>=?1 AND x.k<?2 AND e.d IN (" + in + ") LIMIT 300)";
        Cursor c = db.rawQuery(sql, new String[]{k, hi});
        Map<String, Group> groups = new LinkedHashMap<>();
        try {
            while (c.moveToNext()) {
                Row r = new Row();
                r.k = c.getString(0);
                r.d = c.getInt(1);
                r.hw = c.isNull(2) ? r.k : c.getString(2);
                r.b = c.getInt(3);
                r.i = c.getInt(4);
                String gk = r.hw.toLowerCase(Locale.ROOT);
                Group g = groups.get(gk);
                if (g == null) {
                    g = new Group();
                    g.hw = r.hw;
                    g.k = r.k;
                    groups.put(gk, g);
                }
                boolean dup = false;
                for (Row o : g.rows) if (o.d == r.d && o.b == r.b && o.i == r.i) dup = true;
                if (!dup) g.rows.add(r);
            }
        } finally {
            c.close();
        }
        String qLow = q.trim().toLowerCase(Locale.ROOT);
        List<Group> list = new ArrayList<>(groups.values());
        for (Group g : list) {
            int rank;
            if (g.hw.toLowerCase(Locale.ROOT).equals(qLow)) rank = 0;
            else if (g.k.equals(k)) rank = 1;
            else rank = 2;
            Collections.sort(g.rows, new Comparator<Row>() {
                public int compare(Row a, Row b) { return prio(a.d) - prio(b.d); }
            });
            int distinct = 0, last = -1;
            for (Row r : g.rows) if (r.d != last) { distinct++; last = r.d; }
            // exactness first, then shorter keys, then headwords found in more dictionaries (= more common)
            g.rank = rank * 1000000 + Math.min(g.k.length(), 99) * 1000 - distinct * 10;
        }
        Collections.sort(list, new Comparator<Group>() {
            public int compare(Group a, Group b) {
                if (a.rank != b.rank) return a.rank < b.rank ? -1 : 1;
                return a.hw.compareTo(b.hw);
            }
        });
        int n = 0;
        for (Group g : list) {
            if (n >= max) break;
            JSONObject o = new JSONObject();
            o.put("h", g.hw);
            o.put("k", g.k);
            JSONArray ds = new JSONArray();
            int last = -1;
            for (Row r : g.rows) if (r.d != last) { ds.put(r.d); last = r.d; }
            o.put("d", ds);
            if (n < 30) {
                Row r = g.rows.get(0);
                JSONObject p = new JSONObject();
                p.put("d", r.d);
                p.put("b", body(r.b, r.i));
                o.put("p", p);
            }
            res.put(o);
            n++;
        }
        return res.toString();
    }

    /** All entries of one headword in the enabled dictionaries. */
    public synchronized String detail(String key, String hw, String dictsCsv) throws Exception {
        String in = inList(dictsCsv);
        JSONArray res = new JSONArray();
        Cursor c = db.rawQuery("SELECT d,hw,b,i FROM entries WHERE k=? AND d IN (" + in + ") ORDER BY d", new String[]{key});
        String hwLow = hw == null ? "" : hw.toLowerCase(Locale.ROOT);
        List<Object[]> exact = new ArrayList<>(), other = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                String h = c.isNull(1) ? key : c.getString(1);
                Object[] row = new Object[]{c.getInt(0), h, c.getInt(2), c.getInt(3)};
                if (h.toLowerCase(Locale.ROOT).equals(hwLow)) exact.add(row); else other.add(row);
            }
        } finally {
            c.close();
        }
        List<Object[]> use = exact.isEmpty() ? other : exact;
        for (Object[] r : use) {
            JSONObject o = new JSONObject();
            o.put("d", r[0]);
            o.put("h", r[1]);
            o.put("b", body((Integer) r[2], (Integer) r[3]));
            res.put(o);
        }
        return res.toString();
    }

    public synchronized String meta(String k) {
        Cursor c = db.rawQuery("SELECT v FROM meta WHERE k=?", new String[]{k});
        try {
            return c.moveToFirst() ? c.getString(0) : "";
        } finally {
            c.close();
        }
    }
}
