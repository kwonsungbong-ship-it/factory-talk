package kr.sbkwon.decolock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** NotiStar / Nice Catch: searchable history of notifications, even deleted ones. */
public class HistoryActivity extends Activity {
    private final List<HistoryDb.Item> items = new ArrayList<>();
    private final Map<String, android.graphics.drawable.Drawable> icons = new HashMap<>();
    private BaseAdapter adapter;
    private String query = "";
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Ui ui = new Ui(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.text("알림 기록", 24, Ui.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = ui.text("모두 지우기", 14, ui.accent, true);
        clear.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("알림 기록을 모두 지울까요?")
                .setPositiveButton("지우기", (d, w) -> { HistoryDb.get(this).clear(); load(); })
                .setNegativeButton("취소", null).show());
        top.addView(clear);
        root.addView(top);

        TextView sub = ui.text("최근 30일 · 지워진 알림도 여기 남아요 · 폰 안에만 저장", 13, Ui.SUB, false);
        sub.setPadding(0, ui.dp(4), 0, ui.dp(10));
        root.addView(sub);

        if (!SettingsActivity.listenerEnabled(this)) {
            TextView warn = ui.text("알림 접근 권한이 꺼져 있어 기록되지 않아요. 눌러서 켜기", 14, 0xFFFFFFFF, true);
            warn.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
            Ui.ripple(warn, 0xFFE17055, ui.dp(16));
            warn.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.bottomMargin = ui.dp(10);
            root.addView(warn, wl);
        }

        EditText search = new EditText(this);
        search.setHint("제목·내용·앱 이름 검색");
        search.setSingleLine(true);
        search.setBackground(Ui.round(Ui.CARD, ui.dp(22)));
        search.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable e) { query = e.toString(); load(); }
        });

        empty = ui.text("아직 기록된 알림이 없어요", 14, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, ui.dp(40), 0, 0);
        root.addView(empty);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1);
        llp.topMargin = ui.dp(10);
        root.addView(lv, llp);

        adapter = new BaseAdapter() {
            public int getCount() { return items.size(); }
            public Object getItem(int p) { return items.get(p); }
            public long getItemId(int p) { return items.get(p).id; }
            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout row = (LinearLayout) cv;
                if (row == null) {
                    row = new LinearLayout(HistoryActivity.this);
                    row.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
                    row.setBackground(Ui.round(Ui.CARD, ui.dp(18)));
                    ImageView iv = new ImageView(HistoryActivity.this);
                    row.addView(iv, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
                    LinearLayout col = new LinearLayout(HistoryActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setPadding(ui.dp(12), 0, 0, 0);
                    col.addView(ui.text("", 12, Ui.SUB, false));
                    col.addView(ui.text("", 15, Ui.TEXT, true));
                    TextView body = ui.text("", 14, 0xFF3A3D4A, false);
                    body.setMaxLines(4);
                    col.addView(body);
                    row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
                    ListView.LayoutParams lp = new ListView.LayoutParams(-1, -2);
                    row.setLayoutParams(lp);
                }
                HistoryDb.Item it = items.get(p);
                android.graphics.drawable.Drawable d = icons.get(it.pkg);
                if (d == null && !icons.containsKey(it.pkg)) {
                    try { d = getPackageManager().getApplicationIcon(it.pkg); } catch (Exception ignored) {}
                    icons.put(it.pkg, d);
                }
                ((ImageView) row.getChildAt(0)).setImageDrawable(d);
                LinearLayout col = (LinearLayout) row.getChildAt(1);
                ((TextView) col.getChildAt(0)).setText(it.app + " · " + DateUtils.getRelativeTimeSpanString(it.ts));
                ((TextView) col.getChildAt(1)).setText(it.title);
                ((TextView) col.getChildAt(2)).setText(it.body);
                return row;
            }
        };
        lv.setDivider(new android.graphics.drawable.ColorDrawable(0));
        lv.setDividerHeight(ui.dp(8));
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(items.get(pos).pkg);
            if (i != null) startActivity(i);
        });
        lv.setOnItemLongClickListener((parent, view, pos, id) -> {
            final HistoryDb.Item it = items.get(pos);
            new AlertDialog.Builder(this).setItems(new String[]{"이 알림 지우기", "내용 복사"}, (d, w) -> {
                if (w == 0) { HistoryDb.get(this).delete(it.id); load(); }
                else {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("알림", it.title + "\n" + it.body));
                    Actions.toast(this, "복사했어요");
                }
            }).show();
            return true;
        });

        setContentView(root);
        load();
    }

    private void load() {
        items.clear();
        items.addAll(HistoryDb.get(this).query(query, null, 500));
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }
}
