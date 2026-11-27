package com.r4xn.jejak.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.core.Timeline;

import java.time.LocalDate;
import java.time.ZoneId;

/** A day on the map: where you stopped, how you got between places, and when the signal dropped. */
final class TimelineTab implements MainActivity.Tab {
    private LocalDate showing;

    @Override
    public View build(MainActivity a) {
        Store s = App.store();
        LocalDate today = s.today();
        if (a.date.isAfter(today)) a.date = today;
        LocalDate d = a.date;
        showing = d;

        ScrollView scroll = new ScrollView(a);
        LinearLayout page = Ui.vbox(a);
        page.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 24));
        scroll.addView(page);

        LinearLayout head = Ui.hbox(a);
        head.addView(Ui.title(a, "Timeline"), Ui.weight(1));
        head.addView(Ui.iconButton(a, Icons.LIST, Ui.INK, "Every reading", () -> a.startActivity(PointsActivity.open(a, d, 0, 0))));
        head.addView(Ui.iconButton(a, Icons.SHARE, Ui.INK, "Export", () -> {
            Store.Loaded l = s.cached(d);
            if (l != null) Exporter.ask(a, l);
            else Ui.toast(a, "Still reading the day…");
        }));
        page.addView(head);

        // ‹ Today ›
        LinearLayout row = Ui.hbox(a);
        row.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
        row.addView(Ui.iconButton(a, Icons.LEFT, Ui.INK, "Day before", () -> {
            a.date = d.minusDays(1);
            a.refresh();
        }));
        LinearLayout mid = Ui.vbox(a);
        mid.setGravity(Gravity.CENTER);
        TextView name = Ui.heading(a, Fmt.dayName(d, today));
        name.setGravity(Gravity.CENTER);
        mid.addView(name);
        TextView sub = Ui.muted(a, Fmt.day(d));
        sub.setGravity(Gravity.CENTER);
        mid.addView(sub);
        mid.setOnClickListener(v -> Ui.pickDate(a, d, picked -> {
            a.date = picked.isAfter(today) ? today : picked;
            a.refresh();
        }));
        row.addView(mid, Ui.weight(1));
        MaterialButton next = Ui.iconButton(a, Icons.RIGHT, d.equals(today) ? Ui.LINE : Ui.INK, "Next day", () -> {
            if (d.isBefore(today)) {
                a.date = d.plusDays(1);
                a.refresh();
            }
        });
        next.setEnabled(d.isBefore(today));
        row.addView(next);
        page.addView(row);

        if (s.sample()) page.addView(sampleBanner(a));

        LinearLayout body = Ui.vbox(a);
        page.addView(body, Ui.match());
        Store.Loaded hit = s.cached(d);
        if (hit != null) {
            fill(a, body, hit);
        } else {
            ProgressBar p = new ProgressBar(a);
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = Ui.dp(a, 60);
            body.addView(p, lp);
            TextView t = Ui.muted(a, "Reading the day…");
            t.setGravity(Gravity.CENTER);
            body.addView(t, Ui.match());
            int built = a.page();
            s.load(d, l -> {
                if (!d.equals(showing) || built != a.page() || a.isDestroyed()) return;
                body.removeAllViews();
                fill(a, body, l);
            });
        }
        return scroll;
    }

    static View sampleBanner(MainActivity a) {
        LinearLayout in = Ui.hbox(a);
        LinearLayout texts = Ui.vbox(a);
        texts.addView(Ui.text(a, "Sample days", 15, Ui.WARN, true));
        texts.addView(Ui.text(a, "Made-up readings, not your real location.", 13, Ui.WARN, false));
        in.addView(texts, Ui.weight(1));
        in.addView(Ui.tonal(a, "Turn off", null, Ui.WARN, 0xFFFDE68A, () -> {
            App.store().setSample(false);
            a.refresh();
        }));
        com.google.android.material.card.MaterialCardView card = Ui.card(a, in);
        card.setCardBackgroundColor(Ui.WARN_SOFT);
        card.setStrokeColor(0xFFFDE68A);
        return card;
    }

    private void fill(MainActivity a, LinearLayout body, Store.Loaded l) {
        Store s = App.store();
        ZoneId zone = s.zone();
        Timeline.Day day = l.day;
        if (l.raw.isEmpty() || day.points.isEmpty()) {
            boolean today = l.date.equals(s.today());
            body.addView(Ui.empty(a, Icons.TIMELINE, "Nothing recorded this day",
                    today && !s.recording() ? "Press Start on the Live page and Jejak records where you go, every second."
                            : l.raw.isEmpty() ? "Jejak wasn't recording, or the phone had no signal." : "The readings that day weren't exact enough to show."));
            if (today && !s.recording()) {
                MaterialButton go = Ui.button(a, "Go to Live", Icons.GPS, () -> a.goTo(MainActivity.LIVE));
                LinearLayout.LayoutParams lp = Ui.wrap();
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                body.addView(go, lp);
            }
            return;
        }

        MapCard map = a.map(280);
        map.showDay(day, i -> a.startActivity(ItemActivity.open(a, l.date, i)));
        body.addView(map.card);

        // distance, moving, stops, readings
        LinearLayout stats = Ui.hbox(a);
        stats.addView(stat(a, Fmt.distance(day.meters), "travelled"), Ui.weight(1));
        stats.addView(stat(a, Fmt.duration(day.moving), "moving"), Ui.weight(1));
        stats.addView(stat(a, Integer.toString(day.stops), day.stops == 1 ? "stop" : "stops"), Ui.weight(1));
        stats.addView(stat(a, Fmt.count(day.raw), "readings"), Ui.weight(1));
        body.addView(Ui.card(a, stats));

        body.addView(Ui.section(a, "The day"));
        LinearLayout list = Ui.vbox(a);
        int stop = 0;
        for (int i = 0; i < day.items.size(); i++) {
            Timeline.Item it = day.items.get(i);
            if (it.kind == Timeline.Kind.STAY) stop++;
            int index = i;
            if (i > 0) list.addView(Ui.divider(a));
            LinearLayout r = Ui.row(a, Lines.badge(a, it, stop), Lines.title(s, it), Lines.subtitle(it, zone), null,
                    () -> a.startActivity(ItemActivity.open(a, l.date, index)));
            if (it.kind == Timeline.Kind.GAP) ((TextView) ((LinearLayout) r.getChildAt(2)).getChildAt(0)).setTextColor(Ui.MUTED);
            list.addView(r);
        }
        body.addView(Ui.card(a, list));

        if (!Double.isNaN(day.maxAlt)) {
            TextView foot = Ui.muted(a, "Height " + Fmt.altitude(day.minAlt) + " to " + Fmt.altitude(day.maxAlt)
                    + " above sea level · top speed " + Fmt.speed(day.fastest) + " · "
                    + Fmt.count(day.raw - day.points.size()) + " inexact readings left out");
            foot.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), 0);
            body.addView(foot);
        }
    }

    private static View stat(Context c, String value, String label) {
        LinearLayout v = Ui.vbox(c);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = Ui.text(c, value, 16, Ui.INK, true);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        v.addView(t);
        TextView m = Ui.muted(c, label);
        m.setGravity(Gravity.CENTER);
        v.addView(m);
        return v;
    }

}
