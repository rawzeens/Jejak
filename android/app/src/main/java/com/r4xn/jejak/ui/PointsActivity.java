package com.r4xn.jejak.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Fmt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Every reading of a day (or part of it): time, latitude, longitude, altitude, accuracy and speed. */
public final class PointsActivity extends AppCompatActivity {
    private LocalDate date;
    private long from, to;
    private Store.Loaded loaded;
    private List<Fix> shown = new ArrayList<>();
    private RecyclerView list;
    private LinearLayoutManager layout;
    private ChipGroup hours;
    private MaterialToolbar toolbar;
    private LinearLayout root;

    /** {@code from}/{@code to} of 0 means the whole day. */
    public static Intent open(Context c, LocalDate d, long from, long to) {
        return new Intent(c, PointsActivity.class).putExtra("day", d.toEpochDay()).putExtra("from", from).putExtra("to", to);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        Store s = App.store();
        date = LocalDate.ofEpochDay(getIntent().getLongExtra("day", s.today().toEpochDay()));
        from = getIntent().getLongExtra("from", 0);
        to = getIntent().getLongExtra("to", 0);

        root = Ui.vbox(this);
        root.setBackgroundColor(Ui.PAGE);
        toolbar = new MaterialToolbar(this);
        toolbar.setNavigationIcon(Icons.of(Icons.BACK, Ui.INK, Ui.dp(this, 24)));
        toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(from > 0 ? "Readings" : "Every reading");
        toolbar.setSubtitle(Fmt.dayName(date, s.today()));
        setSupportActionBar(toolbar);
        root.addView(toolbar, Ui.match());

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hours = new ChipGroup(this);
        hours.setSingleLine(true);
        hours.setSingleSelection(true);
        hours.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        hs.addView(hours);
        root.addView(hs, Ui.match());

        LinearLayout head = Ui.hbox(this);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        head.addView(Ui.muted(this, "Time · latitude, longitude"), Ui.weight(1));
        head.addView(Ui.muted(this, "height · accuracy · speed"));
        root.addView(head, Ui.match());

        list = new RecyclerView(this);
        layout = new LinearLayoutManager(this);
        list.setLayoutManager(layout);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        ProgressBar wait = new ProgressBar(this);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 60);
        root.addView(wait, lp);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.fitSystemBars(root);

        s.load(date, l -> {
            root.removeView(wait);
            show(l);
        });
    }

    private void show(Store.Loaded l) {
        loaded = l;
        Store s = App.store();
        ZoneId zone = s.zone();
        shown = new ArrayList<>();
        for (Fix f : l.raw) if (from == 0 || (f.t >= from && f.t <= to)) shown.add(f);
        toolbar.setSubtitle(Fmt.dayName(date, s.today()) + " · " + Fmt.count(shown.size()) + " readings"
                + (from > 0 ? " · " + Fmt.time(from, zone) + " – " + Fmt.time(to, zone) : ""));
        double limit = s.maxAccuracy();
        list.setAdapter(new Rows(shown, zone, limit));
        if (shown.isEmpty()) {
            root.addView(Ui.empty(this, Icons.LIST, "No readings", "Nothing was recorded at this time."), 3);
            return;
        }
        // Jump to an hour
        int lastHour = -1;
        for (int i = 0; i < shown.size(); i++) {
            int h = Instant.ofEpochMilli(shown.get(i).t).atZone(zone).getHour();
            if (h == lastHour) continue;
            lastHour = h;
            int at = i;
            Chip c = Ui.chip(this, (h < 10 ? "0" : "") + h + ":00", true);
            c.setOnClickListener(v -> layout.scrollToPositionWithOffset(at, 0));
            hours.addView(c);
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (loaded != null && !shown.isEmpty()) {
            MenuItem m = menu.add(Menu.NONE, 1, 1, "Export");
            m.setIcon(Icons.of(Icons.SHARE, Ui.INK, Ui.dp(this, 24)));
            m.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1 && loaded != null) {
            if (from == 0) {
                Exporter.ask(this, loaded);
            } else {
                List<Fix> good = new ArrayList<>();
                for (Fix f : loaded.day.points) if (f.t >= from && f.t <= to) good.add(f);
                Exporter.ask(this, loaded, shown, good, " (part)");
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Two lines a reading; readings left out of the timeline are greyed. */
    private static final class Rows extends RecyclerView.Adapter<Rows.Holder> {
        private final List<Fix> fixes;
        private final ZoneId zone;
        private final double limit;

        Rows(List<Fix> fixes, ZoneId zone, double limit) {
            this.fixes = fixes;
            this.zone = zone;
            this.limit = limit;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView top, bottom;

            Holder(LinearLayout v, TextView top, TextView bottom) {
                super(v);
                this.top = top;
                this.bottom = bottom;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout v = Ui.vbox(c);
            v.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 6));
            v.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            TextView top = Ui.text(c, "", 14, Ui.INK, false);
            top.setTypeface(Typeface.MONOSPACE);
            TextView bottom = Ui.text(c, "", 12, Ui.MUTED, false);
            v.addView(top);
            v.addView(bottom);
            return new Holder(v, top, bottom);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int i) {
            Fix f = fixes.get(i);
            boolean out = f.hasAcc() && f.acc > limit;
            h.top.setText(Fmt.clock(f.t, zone) + "  " + Fmt.fixed(f.lat, 7) + ", " + Fmt.fixed(f.lon, 7));
            h.top.setTextColor(out ? Ui.MUTED : Ui.INK);
            h.bottom.setText(Fmt.altitude(f.alt) + " · " + Fmt.accuracy(f.acc) + " · " + Fmt.speed(f.speed)
                    + (out ? " · left out (not exact)" : ""));
            h.itemView.setBackgroundColor(i % 2 == 0 ? Ui.PAGE : 0xFFFFFFFF);
        }

        @Override
        public int getItemCount() {
            return fixes.size();
        }
    }
}
