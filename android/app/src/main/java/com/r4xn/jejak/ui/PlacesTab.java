package com.r4xn.jejak.ui;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.core.Place;

import java.util.List;

/** Named places such as Home and Office. Stops inside a place's circle get its name on the timeline. */
final class PlacesTab implements MainActivity.Tab {
    static final int[] SIZES = {50, 100, 150, 250, 500};

    @Override
    public View build(MainActivity a) {
        Store s = App.store();
        ScrollView scroll = new ScrollView(a);
        LinearLayout page = Ui.vbox(a);
        page.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 24));
        scroll.addView(page);
        TextView title = Ui.title(a, "Places");
        title.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 2));
        page.addView(title);
        TextView sub = Ui.muted(a, "Stops inside a place's circle get its name on your timeline.");
        sub.setPadding(0, 0, 0, Ui.dp(a, 12));
        page.addView(sub);
        if (s.sample()) page.addView(TimelineTab.sampleBanner(a));

        page.addView(Ui.button(a, "Add a place where I am now", Icons.ADD, () -> {
            Fix f = s.latest();
            if (f == null) {
                Ui.toast(a, "No reading yet. Start recording on the Live page first.");
                return;
            }
            edit(a, null, f.lat, f.lon, a::refresh);
        }), Ui.match());
        page.addView(Ui.gap(a, 8));

        List<Place> places = s.places();
        if (places.isEmpty()) {
            page.addView(Ui.empty(a, Icons.PIN, "No places yet",
                    "Tap a stop on your timeline and choose \"Name this place\", or add where you are now."));
            return scroll;
        }
        LinearLayout list = Ui.vbox(a);
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            if (i > 0) list.addView(Ui.divider(a));
            android.widget.ImageView pin = new android.widget.ImageView(a);
            pin.setImageDrawable(Icons.of(Icons.PIN, Ui.ACCENT, Ui.dp(a, 26)));
            list.addView(Ui.row(a, pin, p.name, Fmt.coords(p.lat, p.lon) + " · " + Fmt.fixed(p.radius, 0) + " m circle", null,
                    () -> edit(a, p, p.lat, p.lon, a::refresh)));
        }
        page.addView(Ui.card(a, list));
        return scroll;
    }

    /** Name a new place at a point, or change or delete one. */
    static void edit(Activity a, Place existing, double lat, double lon, Runnable done) {
        Store s = App.store();
        LinearLayout box = Ui.vbox(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        TextInputLayout name = Ui.input(a, "Name, e.g. Home or Office", existing == null ? "" : existing.name,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name);
        box.addView(Ui.muted(a, "Circle size"));
        ChipGroup sizes = new ChipGroup(a);
        sizes.setSingleSelection(true);
        sizes.setSelectionRequired(true);
        double now = existing == null ? 100 : existing.radius;
        for (int m : SIZES) {
            Chip c = Ui.chip(a, m + " m", true);
            c.setId(View.generateViewId());
            c.setTag(m);
            c.setChecked(Math.abs(m - now) < 1);
            sizes.addView(c);
        }
        if (sizes.getCheckedChipId() == View.NO_ID) ((Chip) sizes.getChildAt(1)).setChecked(true);
        box.addView(sizes);
        box.addView(Ui.muted(a, Fmt.coords(lat, lon)));
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a)
                .setTitle(existing == null ? "Name this place" : "Change place")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String n = Ui.value(name);
                    if (n.isEmpty()) n = "Place";
                    Chip c = sizes.findViewById(sizes.getCheckedChipId());
                    double r = c == null ? 100 : (int) c.getTag();
                    if (existing == null) {
                        s.places().add(new Place(Place.newId(s.places()), n, lat, lon, r));
                    } else {
                        existing.name = n;
                        existing.radius = r;
                    }
                    s.savePlaces();
                    if (s.sample()) Ui.toast(a, "Saved for now (sample places aren't kept)");
                    done.run();
                });
        if (existing != null) {
            b.setNeutralButton("Delete", (d, w) -> Ui.confirm(a, "Delete " + existing.name + "?",
                    "Stops there will show as unnamed again. Your readings aren't touched.", "Delete", () -> {
                        s.places().remove(existing);
                        s.savePlaces();
                        done.run();
                    }));
        }
        b.show();
    }
}
