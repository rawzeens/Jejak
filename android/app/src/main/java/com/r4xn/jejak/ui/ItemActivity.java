package com.r4xn.jejak.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.core.Place;
import com.r4xn.jejak.core.Timeline;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** One line of the timeline: a stop, a trip or a time without signal, on its own map. */
public final class ItemActivity extends Screen {
    private LocalDate date;
    private int index;

    public static Intent open(Context c, LocalDate d, int index) {
        return new Intent(c, ItemActivity.class).putExtra("day", d.toEpochDay()).putExtra("index", index);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        date = LocalDate.ofEpochDay(getIntent().getLongExtra("day", store().today().toEpochDay()));
        index = getIntent().getIntExtra("index", 0);
        title("", Fmt.dayName(date, store().today()));
        add(Ui.muted(this, "Reading the day…"));
        store().load(date, this::show);
    }

    private void show(Store.Loaded l) {
        if (isFinishing() || isDestroyed()) return;
        body.removeAllViews();
        List<Timeline.Item> items = l.day.items;
        if (index < 0 || index >= items.size()) {
            add(Ui.empty(this, Icons.TIMELINE, "Not found", "This part of the day has changed. Go back and pick it again."));
            return;
        }
        Store s = store();
        ZoneId zone = s.zone();
        Timeline.Item it = items.get(index);
        title(Lines.title(s, it), Fmt.dayName(date, s.today()));

        if (it.kind != Timeline.Kind.GAP) {
            MapCard map = map(260);
            if (it.kind == Timeline.Kind.STAY) {
                Place p = Timeline.placeOf(it, s.places());
                map.showStop(l.day, it, p != null ? p.radius : s.rules().stayRadius);
            } else {
                map.showTrip(l.day, it);
            }
            add(map.card);
            map.resume();
        }

        LinearLayout facts = Ui.vbox(this);
        switch (it.kind) {
            case STAY -> {
                facts.addView(Ui.pair(this, "Arrived", Fmt.time(it.start, zone), false));
                facts.addView(Ui.pair(this, "Left", it.end >= l.raw.get(l.raw.size() - 1).t && date.equals(s.today()) ? "Still here" : Fmt.time(it.end, zone), false));
                facts.addView(Ui.pair(this, "Stayed", Fmt.duration(it.duration()), true));
                facts.addView(Ui.divider(this));
                facts.addView(Ui.pair(this, "Readings", Fmt.count(it.count()), false));
                facts.addView(Ui.pair(this, "Middle of the place", Fmt.coords(it.lat, it.lon), false));
                String addr = s.address(it.lat, it.lon);
                if (addr != null && Timeline.placeOf(it, s.places()) != null) facts.addView(Ui.pair(this, "Address", addr, false));
            }
            case MOVE -> {
                facts.addView(Ui.pair(this, "Left", Fmt.time(it.start, zone), false));
                facts.addView(Ui.pair(this, "Arrived", Fmt.time(it.end, zone), false));
                facts.addView(Ui.pair(this, "Took", Fmt.duration(it.duration()), true));
                facts.addView(Ui.divider(this));
                facts.addView(Ui.pair(this, "Distance", Fmt.distance(it.meters), false));
                facts.addView(Ui.pair(this, "Average speed", Fmt.speed(it.average()), false));
                facts.addView(Ui.pair(this, "Top speed", Fmt.speed(it.fast), false));
                facts.addView(Ui.pair(this, "Climbed", Fmt.altitude(it.climb), false));
                facts.addView(Ui.pair(this, "Went down", Fmt.altitude(it.descent), false));
                facts.addView(Ui.pair(this, "Readings", Fmt.count(it.count()), false));
            }
            default -> {
                facts.addView(Ui.pair(this, "From", Fmt.time(it.start, zone), false));
                facts.addView(Ui.pair(this, "To", Fmt.time(it.end, zone), false));
                facts.addView(Ui.pair(this, "No readings for", Fmt.duration(it.duration()), true));
                TextView why = Ui.muted(this, "The phone gave no position. This happens in tunnels and underground car parks, "
                        + "when the phone is off, or when recording was stopped.");
                why.setPadding(0, Ui.dp(this, 8), 0, 0);
                facts.addView(why);
            }
        }
        add(Ui.card(this, facts));

        if (it.kind == Timeline.Kind.STAY) {
            Place p = Timeline.placeOf(it, s.places());
            add(wide(Ui.button(this, p == null ? "Name this place" : "Change " + p.name, Icons.EDIT,
                    () -> PlacesTab.edit(this, p, p == null ? it.lat : p.lat, p == null ? it.lon : p.lon, this::reload))));
            if (p == null) {
                add(wide(Ui.outlined(this, s.address(it.lat, it.lon) == null ? "Look up the address" : "Look up the address again", Icons.SEARCH,
                        () -> lookUp(it.lat, it.lon, a -> {
                            if (a == null) {
                                Ui.toast(this, "No address found (needs internet).");
                                return;
                            }
                            s.setAddress(it.lat, it.lon, a);
                            reload();
                        }))));
            }
            add(wide(Ui.outlined(this, "Open in another map app", Icons.MAP, () -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + it.lat + "," + it.lon + "?q=" + it.lat + "," + it.lon)));
                } catch (ActivityNotFoundException e) {
                    Ui.toast(this, "No map app on this phone.");
                }
            })));
        }
        if (it.kind != Timeline.Kind.GAP) {
            add(wide(Ui.outlined(this, "See every reading", Icons.LIST, () -> startActivity(PointsActivity.open(this, date, it.start, it.end)))));
        }
    }

    private android.view.View wide(android.view.View v) {
        LinearLayout.LayoutParams lp = Ui.match();
        lp.bottomMargin = Ui.dp(this, 6);
        v.setLayoutParams(lp);
        return v;
    }

    private void reload() {
        store().changed();
        body.removeAllViews();
        store().load(date, this::show);
    }

    /** Street address from the phone's map service (needs internet). */
    private void lookUp(double lat, double lon, Consumer<String> done) {
        if (!Geocoder.isPresent()) {
            done.accept(null);
            return;
        }
        Geocoder g = new Geocoder(this, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= 33) {
            g.getFromLocation(lat, lon, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(List<Address> list) {
                    runOnUiThread(() -> done.accept(shortAddress(list)));
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> done.accept(null));
                }
            });
        } else {
            App.store().background(() -> shortAddress(legacyLookUp(g, lat, lon)), done);
        }
    }

    @SuppressWarnings("deprecation")
    private static List<Address> legacyLookUp(Geocoder g, double lat, double lon) throws java.io.IOException {
        return g.getFromLocation(lat, lon, 1);
    }

    /** "12, Jalan Telawi 3, Bangsar" from the full address. */
    static String shortAddress(List<Address> list) {
        if (list == null || list.isEmpty()) return null;
        String line = list.get(0).getAddressLine(0);
        if (line == null) return null;
        String[] parts = line.split(",\\s*");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length && i < 3; i++) {
            if (i > 0) b.append(", ");
            b.append(parts[i]);
        }
        return b.toString();
    }
}
