package com.r4xn.jejak.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.rec.RecordService;

import java.util.function.IntConsumer;

/** How often to record, how to read the day, and the readings kept on the phone. */
final class SettingsTab implements MainActivity.Tab {
    @Override
    public View build(MainActivity a) {
        Store s = App.store();
        ScrollView scroll = new ScrollView(a);
        LinearLayout page = Ui.vbox(a);
        page.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 24));
        scroll.addView(page);
        TextView title = Ui.title(a, "Settings");
        title.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        page.addView(title);

        page.addView(Ui.section(a, "Recording"));
        LinearLayout rec = Ui.vbox(a);
        rec.addView(choice(a, "Record every", s.interval(), Store.INTERVALS, SettingsTab::every, v -> {
            s.setInterval(v);
            RecordService.restart(a);
            a.refresh();
        }));
        rec.addView(Ui.divider(a));
        rec.addView(toggle(a, "Wi-Fi and mobile network indoors", "Fills in when there's no GPS signal. Less exact.", s.indoor(), on -> {
            s.setIndoor(on);
            RecordService.restart(a);
        }));
        rec.addView(Ui.divider(a));
        rec.addView(toggle(a, "Start again after the phone restarts", "Needs location set to \"Allow all the time\".", s.restart(), on -> {
            s.setRestart(on);
            if (on && !MainActivity.always(a)) a.askAlways();
        }));
        page.addView(Ui.card(a, rec));
        TextView battery = Ui.muted(a, "Every second gives the most detail but uses the most battery: about as much as a navigation app. "
                + "Every 5 or 10 seconds still shows the day well and lasts much longer.");
        battery.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        page.addView(battery);

        page.addView(Ui.section(a, "Timeline"));
        LinearLayout tl = Ui.vbox(a);
        tl.addView(choice(a, "Leave out readings less exact than", s.maxAccuracy(), Store.ACCURACIES, m -> m + " m", v -> {
            s.setMaxAccuracy(v);
            a.refresh();
        }));
        tl.addView(Ui.divider(a));
        tl.addView(choice(a, "A stop is at least", s.minStay(), Store.STOPS, m -> m + " min", v -> {
            s.setMinStay(v);
            a.refresh();
        }));
        page.addView(Ui.card(a, tl));

        page.addView(Ui.section(a, "Your readings"));
        LinearLayout data = Ui.vbox(a);
        LinearLayout size = Ui.row(a, null, "Space used", "Counting…", null, null);
        TextView sizeText = (TextView) ((LinearLayout) size.getChildAt(0)).getChildAt(1);
        data.addView(size);
        s.background(() -> new long[]{s.db().bytes(), s.db().total(), s.db().oldest()}, r -> {
            if (r == null) return;
            sizeText.setText(Fmt.bytes(r[0]) + " · " + Fmt.count(r[1]) + " readings"
                    + (r[2] > 0 ? " since " + Fmt.day(java.time.Instant.ofEpochMilli(r[2]).atZone(s.zone()).toLocalDate()) : ""));
        });
        data.addView(Ui.divider(a));
        data.addView(choice(a, "Keep readings for", s.keepDays(), Store.KEEP, SettingsTab::keep, v -> {
            if (v > 0 && (s.keepDays() == 0 || v < s.keepDays())) {
                Ui.confirm(a, "Keep readings for " + keep(v).toLowerCase(java.util.Locale.ROOT) + "?",
                        "Readings older than that are deleted now and from then on.", "Delete older", () -> {
                            s.setKeepDays(v);
                            a.refresh();
                        });
            } else {
                s.setKeepDays(v);
                a.refresh();
            }
        }));
        data.addView(Ui.divider(a));
        data.addView(Ui.row(a, null, "Delete a day…", "Pick a day to remove its readings", null, () ->
                Ui.pickDate(a, s.today(), d -> Ui.confirm(a, "Delete " + Fmt.day(d) + "?", "All readings from that day are removed from this phone.",
                        "Delete", () -> s.background(() -> {
                            s.deleteDay(d);
                            return true;
                        }, ok -> {
                            Ui.toast(a, "Day deleted");
                            a.refresh();
                        })))));
        data.addView(Ui.divider(a));
        LinearLayout all = Ui.row(a, null, "Delete everything", "Every reading on this phone. This can't be undone.", null, () ->
                Ui.confirm(a, "Delete every reading?", "Your whole timeline is removed from this phone. Places are kept.", "Delete everything",
                        () -> s.background(() -> {
                            s.deleteAll();
                            return true;
                        }, ok -> {
                            Ui.toast(a, "Everything deleted");
                            a.refresh();
                        })));
        ((TextView) ((LinearLayout) all.getChildAt(0)).getChildAt(0)).setTextColor(Ui.BAD);
        data.addView(all);
        data.addView(Ui.divider(a));
        data.addView(toggle(a, "Show sample days", "Two weeks of made-up days to see how Jejak works.", s.sample(), on -> {
            s.setSample(on);
            a.date = s.today();
            a.refresh();
        }));
        page.addView(Ui.card(a, data));

        page.addView(Ui.section(a, "If recording stops by itself"));
        LinearLayout tips = Ui.vbox(a);
        tips.addView(Ui.body(a, "Some phones (Xiaomi, Redmi, POCO, OPPO, realme, vivo, Huawei, Honor and some Samsung) close apps in the "
                + "background to save battery. If your timeline has holes:"));
        tips.addView(Ui.gap(a, 6));
        tips.addView(Ui.body(a, "• Set Jejak's battery use to \"No restrictions\" or \"Unrestricted\".\n"
                + "• Turn on \"Autostart\" for Jejak if your phone has it.\n"
                + "• Lock Jejak in the recent apps list, and don't swipe it away."));
        tips.addView(Ui.gap(a, 10));
        LinearLayout btns = Ui.hbox(a);
        btns.addView(Ui.outlined(a, "Battery", Icons.BATTERY, a::askBattery), Ui.weight(1));
        btns.addView(Ui.gap(a, 8));
        btns.addView(Ui.outlined(a, "App settings", Icons.SETTINGS, a::openAppSettings), Ui.weight(1));
        tips.addView(btns);
        page.addView(Ui.card(a, tips));

        page.addView(Ui.section(a, "About"));
        LinearLayout about = Ui.vbox(a);
        about.addView(Ui.body(a, "Jejak 1.0.0"));
        about.addView(Ui.muted(a, "Your readings stay on this phone. Nothing is sent anywhere unless you export it. "
                + "Maps © OpenStreetMap contributors."));
        page.addView(Ui.card(a, about));
        return scroll;
    }

    static String every(int s) {
        return s == 1 ? "1 second" : s < 60 ? s + " seconds" : "1 minute";
    }

    static String keep(int d) {
        return d == 0 ? "Forever" : d == 365 ? "1 year" : d + " days";
    }

    interface Label {
        String of(int v);
    }

    private static LinearLayout choice(MainActivity a, String title, int now, int[] options, Label label, IntConsumer picked) {
        return Ui.row(a, null, title, label.of(now), null, () -> {
            String[] names = new String[options.length];
            int at = -1;
            for (int i = 0; i < options.length; i++) {
                names[i] = label.of(options[i]);
                if (options[i] == now) at = i;
            }
            new MaterialAlertDialogBuilder(a)
                    .setTitle(title)
                    .setSingleChoiceItems(names, at, (d, w) -> {
                        d.dismiss();
                        if (options[w] != now) picked.accept(options[w]);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    interface Flip {
        void set(boolean on);
    }

    private static View toggle(MainActivity a, String title, String sub, boolean on, Flip flip) {
        MaterialSwitch sw = new MaterialSwitch(a);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, v) -> flip.set(v));
        LinearLayout r = Ui.row(a, null, title, sub, sw, () -> sw.setChecked(!sw.isChecked()));
        return r;
    }
}
