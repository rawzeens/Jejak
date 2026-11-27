package com.r4xn.jejak.ui;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Export;
import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Fmt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Saves a day's readings to a file and hands it to the share sheet (email, Drive, WhatsApp…). */
final class Exporter {
    private Exporter() {
    }

    static void ask(Activity a, Store.Loaded l) {
        ask(a, l, l.raw, l.day.points, "");
    }

    /** {@code all}: every reading for the spreadsheet; {@code good}: the exact ones for the GPS track. */
    static void ask(Activity a, Store.Loaded l, List<Fix> all, List<Fix> good, String part) {
        if (all.isEmpty()) {
            Ui.toast(a, "Nothing recorded to export.");
            return;
        }
        new MaterialAlertDialogBuilder(a)
                .setTitle("Export " + Fmt.dayName(l.date, App.store().today()) + part)
                .setItems(new String[]{"Spreadsheet (CSV) · every reading", "GPS track (GPX) · for maps and fitness apps"},
                        (d, w) -> share(a, l, w == 1 ? good : all, w == 1, part))
                .show();
    }

    private static void share(Activity a, Store.Loaded l, List<Fix> pts, boolean gpx, String part) {
        Store s = App.store();
        Ui.toast(a, "Getting the file ready…");
        String name = "jejak-" + Fmt.date(l.date) + (part.isEmpty() ? "" : "-part") + (gpx ? ".gpx" : ".csv");
        s.background(() -> {
            File dir = new File(a.getCacheDir(), "shared");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            File f = new File(dir, name);
            try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 1 << 16)) {
                if (gpx) Export.gpx(pts, "Jejak " + Fmt.day(l.date), s.rules().gap, w);
                else Export.csv(pts, s.zone(), w);
            }
            return f;
        }, f -> {
            if (f == null) Ui.toast(a, "Couldn't make the file (is the phone full?)");
            else Ui.shareFile(a, f, gpx ? "application/gpx+xml" : "text/csv", "Jejak " + Fmt.day(l.date));
        });
    }
}
