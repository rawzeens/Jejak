package com.r4xn.jejak.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.rec.Live;

import java.time.ZoneId;
import java.util.List;

/** Recording on or off, and the position as it comes in, every second. */
final class LiveTab implements MainActivity.Tab, Live.Listener {
    private MainActivity a;
    private TextView status, statusSub, lat, lon, alt, acc, speed, updated, today, storage;
    private View dot;
    private MaterialButton toggle;
    private LinearLayout recent;
    private long recentTop = -1;
    private boolean wasRunning;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            changed();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    public View build(MainActivity a) {
        this.a = a;
        Store s = App.store();
        ScrollView scroll = new ScrollView(a);
        LinearLayout page = Ui.vbox(a);
        page.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 24));
        scroll.addView(page);
        TextView title = Ui.title(a, "Live");
        title.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));
        page.addView(title);
        if (s.sample()) page.addView(TimelineTab.sampleBanner(a));

        // On / off
        LinearLayout st = Ui.vbox(a);
        LinearLayout line = Ui.hbox(a);
        dot = new View(a);
        line.addView(dot, new LinearLayout.LayoutParams(Ui.dp(a, 12), Ui.dp(a, 12)));
        line.addView(Ui.gap(a, 10));
        status = Ui.text(a, "", 20, Ui.INK, true);
        line.addView(status, Ui.weight(1));
        st.addView(line);
        statusSub = Ui.muted(a, "");
        statusSub.setPadding(Ui.dp(a, 22), Ui.dp(a, 2), 0, Ui.dp(a, 12));
        st.addView(statusSub);
        toggle = Ui.button(a, "", null, () -> {
            if (Live.running || s.recording()) a.stopRecording();
            else a.startRecording();
        });
        toggle.setMinHeight(Ui.dp(a, 52));
        st.addView(toggle, Ui.match());
        page.addView(Ui.card(a, st));

        problems(a, page);

        // Right now
        page.addView(Ui.section(a, "Right now"));
        LinearLayout now = Ui.vbox(a);
        lat = big(a, now, "Latitude");
        lon = big(a, now, "Longitude");
        alt = big(a, now, "Altitude");
        now.addView(Ui.gap(a, 6));
        LinearLayout two = Ui.hbox(a);
        acc = small(a, two, "Accuracy");
        speed = small(a, two, "Speed");
        now.addView(two);
        updated = Ui.muted(a, "");
        updated.setPadding(0, Ui.dp(a, 10), 0, 0);
        now.addView(updated);
        page.addView(Ui.card(a, now));

        // Today
        page.addView(Ui.section(a, "Today"));
        LinearLayout t = Ui.hbox(a);
        today = small(a, t, "Readings today");
        storage = small(a, t, "Space used");
        page.addView(Ui.card(a, t));
        s.background(() -> s.db().bytes(), b -> {
            if (b != null) storage.setText(Fmt.bytes(b));
        });

        // Last readings
        page.addView(Ui.section(a, "Last readings"));
        recent = Ui.vbox(a);
        page.addView(Ui.card(a, recent));

        Live.listen(this);
        wasRunning = Live.running;
        h.post(tick);
        return scroll;
    }

    @Override
    public void closed() {
        Live.stopListening(this);
        h.removeCallbacks(tick);
    }

    /** Things that would stop recording, each with the button that fixes it. */
    private void problems(MainActivity a, LinearLayout page) {
        Store s = App.store();
        if (!s.recording()) return;
        if (!MainActivity.locationOn(a)) {
            page.addView(warning(a, Icons.GPS_OFF, "Location is off", "Turn on Location so the phone can find where it is.",
                    "Turn on", a::openLocationSettings));
        }
        if (!MainActivity.precise(a)) {
            page.addView(warning(a, Icons.WARNING, "Precise location is off", "Readings can be a few kilometres out. Turn on \"Use precise location\".",
                    "Open settings", a::openAppSettings));
        }
        if (!MainActivity.batteryFree(a)) {
            page.addView(warning(a, Icons.BATTERY, "Battery saving may stop recording",
                    "Let Jejak run in the background so the phone doesn't pause it to save battery.", "Allow", a::askBattery));
        }
        if (s.restart() && !MainActivity.always(a)) {
            page.addView(warning(a, Icons.INFO, "Keep recording after a restart",
                    "Choose \"Allow all the time\" so Jejak starts again by itself when the phone restarts.", "Allow", a::askAlways));
        }
    }

    static View warning(Context c, String icon, String title, String text, String button, Runnable fix) {
        LinearLayout in = Ui.vbox(c);
        LinearLayout head = Ui.hbox(c);
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setImageDrawable(Icons.of(icon, Ui.WARN, Ui.dp(c, 20)));
        head.addView(iv);
        head.addView(Ui.gap(c, 8));
        head.addView(Ui.text(c, title, 15, Ui.INK, true), Ui.weight(1));
        in.addView(head);
        TextView m = Ui.text(c, text, 14, Ui.INK, false);
        m.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        in.addView(m);
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.gravity = Gravity.END;
        in.addView(Ui.tonal(c, button, null, Ui.WARN, 0xFFFDE68A, fix), lp);
        MaterialCardView card = Ui.card(c, in);
        card.setCardBackgroundColor(Ui.WARN_SOFT);
        card.setStrokeColor(0xFFFDE68A);
        return card;
    }

    private static TextView big(Context c, LinearLayout parent, String label) {
        LinearLayout r = Ui.hbox(c);
        r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        TextView l = Ui.muted(c, label);
        l.setTextSize(14);
        r.addView(l, Ui.weight(1));
        TextView v = Ui.text(c, "–", 24, Ui.INK, true);
        v.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        v.setGravity(Gravity.END);
        r.addView(v);
        parent.addView(r);
        return v;
    }

    private static TextView small(Context c, LinearLayout parent, String label) {
        LinearLayout v = Ui.vbox(c);
        v.addView(Ui.muted(c, label));
        TextView t = Ui.text(c, "–", 18, Ui.INK, true);
        v.addView(t);
        parent.addView(v, Ui.weight(1));
        return t;
    }

    @Override
    public void changed() {
        if (status == null || a == null || a.isDestroyed()) return;
        Store s = App.store();
        if (Live.running != wasRunning) {
            wasRunning = Live.running;
            a.refresh();
            return;
        }
        boolean on = Live.running;
        Fix f = s.latest();
        long age = f == null ? Long.MAX_VALUE : s.now() - f.t;
        boolean waiting = on && !s.sample() && age > 15_000;
        status.setText(on ? (waiting ? "Waiting for a signal…" : "Recording") : s.recording() ? "Starting…" : "Stopped");
        dot.setBackground(Ui.round(on ? (waiting ? Ui.WARN : Ui.GOOD) : Ui.MUTED, Ui.dp(a, 6)));
        int every = s.interval();
        String how = every == 1 ? "Every second" : "Every " + Fmt.duration(every * 1000L).replace(" s", " seconds");
        if (Live.problem != null) statusSub.setText(Live.problem);
        else if (on && !Live.source.isEmpty())
            statusSub.setText(how + " · " + Live.source + (Live.satsSeen > 0 ? " · " + Live.satsUsed + " of " + Live.satsSeen + " satellites" : ""));
        else if (on) statusSub.setText(how + " · looking for GPS");
        else statusSub.setText("Press Start to record where you go, even with the screen off.");
        toggle.setText(on || s.recording() ? "Stop recording" : "Start recording");
        toggle.setIcon(Icons.of(on || s.recording() ? Icons.STOP : Icons.PLAY, android.graphics.Color.WHITE, Ui.dp(a, 20)));
        toggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on || s.recording() ? Ui.BAD : Ui.BRAND));

        if (f != null) {
            lat.setText(Fmt.fixed(f.lat, 7));
            lon.setText(Fmt.fixed(f.lon, 7));
            alt.setText(f.hasAlt() ? Fmt.fixed(f.alt, 1) + " m" : "–");
            acc.setText(Fmt.accuracy(f.acc));
            speed.setText(Fmt.speed(f.speed));
            updated.setText(age < 2000 ? "Updated just now" : "Updated " + Fmt.duration(age) + " ago · " + Fmt.clock(f.t, s.zone()));
        } else {
            updated.setText(on ? "Waiting for the first reading…" : "No readings yet.");
        }
        today.setText(s.sample() ? Fmt.count(s.readings(s.today()).size()) : Fmt.count(Live.today));
        showRecent(s);
    }

    private void showRecent(Store s) {
        List<Fix> list = s.recent();
        long top = list.isEmpty() ? 0 : list.get(0).t;
        if (top == recentTop) return;
        recentTop = top;
        recent.removeAllViews();
        if (list.isEmpty()) {
            recent.addView(Ui.muted(a, "Readings show here as they come in."));
            return;
        }
        ZoneId zone = s.zone();
        LinearLayout head = Ui.hbox(a);
        head.addView(cell("Time", 0.9f, true));
        head.addView(cell("Latitude", 1.2f, true));
        head.addView(cell("Longitude", 1.35f, true));
        head.addView(cell("Alt.", 0.65f, true));
        recent.addView(head);
        for (Fix f : list) {
            LinearLayout r = Ui.hbox(a);
            r.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 3));
            r.addView(cell(Fmt.clock(f.t, zone), 0.9f, false));
            r.addView(cell(Fmt.fixed(f.lat, 6), 1.2f, false));
            r.addView(cell(Fmt.fixed(f.lon, 6), 1.35f, false));
            r.addView(cell(f.hasAlt() ? Fmt.fixed(f.alt, 0) : "–", 0.65f, false));
            recent.addView(r);
        }
    }

    private TextView cell(String text, float weight, boolean head) {
        TextView t = head ? Ui.muted(a, text) : Ui.text(a, text, 13, Ui.INK, false);
        if (!head) t.setTypeface(Typeface.MONOSPACE);
        t.setMaxLines(1);
        t.setLayoutParams(Ui.weight(weight));
        return t;
    }
}
