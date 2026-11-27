package com.r4xn.jejak.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.r4xn.jejak.Store;
import com.r4xn.jejak.core.Fmt;
import com.r4xn.jejak.core.Place;
import com.r4xn.jejak.core.Timeline;

import java.time.ZoneId;

/** How a line of the timeline is named, described and drawn. */
final class Lines {
    private Lines() {
    }

    static String title(Store s, Timeline.Item it) {
        switch (it.kind) {
            case STAY: {
                Place p = Timeline.placeOf(it, s.places());
                if (p != null) return p.name;
                String a = s.address(it.lat, it.lon);
                return a != null ? a : "Unnamed place";
            }
            case MOVE:
                return it.mode.label + " · " + Fmt.distance(it.meters);
            default:
                return "No signal";
        }
    }

    static String times(Timeline.Item it, ZoneId zone) {
        return Fmt.time(it.start, zone) + " – " + Fmt.time(it.end, zone) + " · " + Fmt.duration(it.duration());
    }

    static String subtitle(Timeline.Item it, ZoneId zone) {
        return it.kind == Timeline.Kind.MOVE ? times(it, zone) + " · " + Fmt.speed(it.average()) : times(it, zone);
    }

    static String icon(Timeline.Item it) {
        if (it.kind == Timeline.Kind.GAP) return Icons.GPS_OFF;
        if (it.kind == Timeline.Kind.STAY) return Icons.PIN;
        switch (it.mode) {
            case WALK:
                return Icons.WALK;
            case CYCLE:
                return Icons.BIKE;
            case FLIGHT:
                return Icons.PLANE;
            default:
                return Icons.CAR;
        }
    }

    /** Round badge: the stop's number (as on the map), or the way of travelling. */
    static View badge(Context c, Timeline.Item it, int number) {
        FrameLayout f = new FrameLayout(c);
        int size = Ui.dp(c, 40);
        f.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        if (it.kind == Timeline.Kind.STAY) {
            f.setBackground(Ui.round(Ui.ACCENT, size));
            TextView t = Ui.text(c, Integer.toString(number), 15, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        } else {
            boolean gap = it.kind == Timeline.Kind.GAP;
            f.setBackground(Ui.round(gap ? Ui.GREY_SOFT : Ui.BRAND_SOFT, size));
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(Icons.of(icon(it), gap ? Ui.MUTED : Ui.BRAND, Ui.dp(c, 22)));
            f.addView(iv, new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.CENTER));
        }
        return f;
    }
}
