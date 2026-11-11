package kr.sbkwon.decolock;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.content.res.ColorStateList;

import java.util.ArrayList;
import java.util.List;

/** Multi-select app chooser used for panel apps, home apps and dock. */
public class AppPickerActivity extends Activity {
    public static final String EXTRA_KEY = "key", EXTRA_TITLE = "title", EXTRA_MAX = "max";

    private String key;
    private int max;
    private final List<String> selected = new ArrayList<>();
    private List<Apps.App> all, shown;
    private TextView count;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        key = getIntent().getStringExtra(EXTRA_KEY);
        max = getIntent().getIntExtra(EXTRA_MAX, 40);
        selected.addAll(Prefs.list(this, key));
        all = Apps.all(this);
        shown = new ArrayList<>(all);
        final int accent = Prefs.accent(this);

        Ui ui = new Ui(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(12));

        TextView title = ui.text(getIntent().getStringExtra(EXTRA_TITLE), 22, Ui.TEXT, true);
        root.addView(title);
        count = ui.text("", 13, Ui.SUB, false);
        count.setPadding(0, ui.dp(4), 0, ui.dp(10));
        root.addView(count);

        EditText search = new EditText(this);
        search.setHint("앱 검색");
        search.setSingleLine(true);
        search.setBackground(Ui.round(Ui.CARD, ui.dp(22)));
        search.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable e) {
                String q = e.toString().trim().toLowerCase();
                shown.clear();
                for (Apps.App a : all) if (q.isEmpty() || a.label.toLowerCase().contains(q)) shown.add(a);
                adapter.notifyDataSetChanged();
            }
        });

        ListView lv = new ListView(this);
        lv.setDivider(null);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1);
        llp.topMargin = ui.dp(10);
        lv.setBackground(Ui.round(Ui.CARD, ui.dp(20)));
        lv.setClipToOutline(true);
        root.addView(lv, llp);

        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int p) { return shown.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout row = (LinearLayout) cv;
                if (row == null) {
                    row = new LinearLayout(AppPickerActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
                    ImageView iv = new ImageView(AppPickerActivity.this);
                    row.addView(iv, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
                    TextView tv = ui.text("", 16, Ui.TEXT, false);
                    tv.setPadding(ui.dp(14), 0, 0, 0);
                    row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
                    CheckBox cb = new CheckBox(AppPickerActivity.this);
                    cb.setClickable(false);
                    cb.setFocusable(false);
                    cb.setButtonTintList(ColorStateList.valueOf(accent));
                    row.addView(cb);
                }
                Apps.App a = shown.get(p);
                ((ImageView) row.getChildAt(0)).setImageDrawable(Apps.icon(AppPickerActivity.this, a));
                ((TextView) row.getChildAt(1)).setText(a.label);
                ((CheckBox) row.getChildAt(2)).setChecked(selected.contains(a.key));
                return row;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Apps.App a = shown.get(pos);
            if (selected.contains(a.key)) selected.remove(a.key);
            else if (selected.size() < max) selected.add(a.key);
            else Actions.toast(this, "최대 " + max + "개까지 고를 수 있어요");
            adapter.notifyDataSetChanged();
            updateCount();
        });

        TextView save = ui.text("저장", 16, 0xFFFFFFFF, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(0, ui.dp(14), 0, ui.dp(14));
        Ui.ripple(save, accent, ui.dp(28));
        save.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = ui.dp(12);
        root.addView(save, slp);

        setContentView(root);
        updateCount();
    }

    private void updateCount() {
        count.setText(selected.size() + " / " + max + "개 선택 · 고른 순서대로 표시돼요");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // drop stale keys of uninstalled apps
        List<String> keep = new ArrayList<>();
        for (String k : selected) if (Apps.find(this, k) != null) keep.add(k);
        Prefs.putList(this, key, keep);
        if (DecoAccessibility.instance != null) DecoAccessibility.instance.refresh();
    }
}
