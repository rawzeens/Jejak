package com.r4xn.jejak.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Geo;
import com.r4xn.jejak.core.Timeline;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** An OpenStreetMap map in a rounded card, with the day's track and numbered stops. */
public final class MapCard {
    public final MaterialCardView card;
    public final MapView map;
    private final Context c;
    private double[] box;

    @SuppressLint("ClickableViewAccessibility")
    MapCard(Context c, int heightDp) {
        this.c = c;
        map = new MapView(c);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(19.0);
        map.getController().setZoom(12.0);
        map.getController().setCenter(new GeoPoint(3.139, 101.6869));
        map.getOverlays().add(new CopyrightOverlay(c));
        // Let the map have the finger inside a scrolling page.
        map.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        card = new MaterialCardView(c);
        card.setRadius(Ui.dp(c, 16));
        card.setCardElevation(0);
        card.setStrokeColor(Ui.LINE);
        card.setStrokeWidth(Ui.dp(c, 1));
        card.addView(map, new android.widget.FrameLayout.LayoutParams(-1, Ui.dp(c, heightDp)));
        LinearLayout.LayoutParams lp = Ui.match();
        lp.bottomMargin = Ui.dp(c, 12);
        card.setLayoutParams(lp);
    }

    /** The whole day: the track, and a numbered dot for each stop. */
    public void showDay(Timeline.Day day, IntConsumer onStop) {
        List<Fix> pts = day.points;
        if (pts.isEmpty()) return;
        line(Geo.simplify(pts, 0, pts.size() - 1, 12), Ui.BRAND, 5);
        // Stops at the same place share one dot: "1,6".
        List<Timeline.Item> spots = new ArrayList<>();
        List<StringBuilder> labels = new ArrayList<>();
        List<Integer> first = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < day.items.size(); i++) {
            Timeline.Item it = day.items.get(i);
            if (it.kind != Timeline.Kind.STAY) continue;
            n++;
            int at = -1;
            for (int k = 0; k < spots.size() && at < 0; k++) {
                if (Geo.distance(spots.get(k).lat, spots.get(k).lon, it.lat, it.lon) < 150) at = k;
            }
            if (at >= 0) {
                labels.get(at).append(',').append(n);
            } else {
                spots.add(it);
                labels.add(new StringBuilder(Integer.toString(n)));
                first.add(i);
            }
        }
        for (int k = 0; k < spots.size(); k++) {
            int index = first.get(k);
            dot(spots.get(k).lat, spots.get(k).lon, labels.get(k).toString(), Ui.ACCENT, onStop == null ? null : () -> onStop.accept(index));
        }
        fit(Geo.bounds(pts));
    }

    /** One trip: the whole day faint, this trip bold. */
    public void showTrip(Timeline.Day day, Timeline.Item trip) {
        List<Fix> pts = day.points;
        line(Geo.simplify(pts, 0, pts.size() - 1, 12), 0x551D4ED8, 4);
        List<Fix> part = Geo.simplify(pts, trip.from, trip.to, 4);
        line(part, Ui.BRAND, 6);
        Fix a = pts.get(trip.from), b = pts.get(trip.to);
        dot(a.lat, a.lon, "A", Ui.GOOD, null);
        dot(b.lat, b.lon, "B", Ui.BAD, null);
        fit(Geo.bounds(part));
    }

    /** One stop: its circle and the middle of it. */
    public void showStop(Timeline.Day day, Timeline.Item stop, double radius) {
        Polygon circle = new Polygon(map);
        circle.setPoints(Polygon.pointsAsCircle(new GeoPoint(stop.lat, stop.lon), radius));
        circle.getFillPaint().setColor(0x221D4ED8);
        circle.getOutlinePaint().setColor(Ui.BRAND);
        circle.getOutlinePaint().setStrokeWidth(Ui.dp(c, 2));
        circle.setInfoWindow(null);
        map.getOverlays().add(circle);
        dot(stop.lat, stop.lon, "", Ui.ACCENT, null);
        double r = radius / 111_000.0 * 1.6;
        fit(new double[]{stop.lat - r, stop.lon - r, stop.lat + r, stop.lon + r});
    }

    /** Just a point (a named place, or where the phone is now). */
    public void showPoint(double lat, double lon, double radius) {
        if (radius > 0) {
            Polygon circle = new Polygon(map);
            circle.setPoints(Polygon.pointsAsCircle(new GeoPoint(lat, lon), radius));
            circle.getFillPaint().setColor(0x221D4ED8);
            circle.getOutlinePaint().setColor(Ui.BRAND);
            circle.getOutlinePaint().setStrokeWidth(Ui.dp(c, 2));
            circle.setInfoWindow(null);
            map.getOverlays().add(circle);
        }
        dot(lat, lon, "", Ui.BRAND, null);
        double r = Math.max(radius, 150) / 111_000.0 * 1.6;
        fit(new double[]{lat - r, lon - r, lat + r, lon + r});
    }

    private void line(List<Fix> pts, int color, int widthDp) {
        if (pts.size() < 2) return;
        List<GeoPoint> g = new ArrayList<>(pts.size());
        for (Fix p : pts) g.add(new GeoPoint(p.lat, p.lon));
        Polyline l = new Polyline(map);
        l.setPoints(g);
        Paint paint = l.getOutlinePaint();
        paint.setColor(color);
        paint.setStrokeWidth(Ui.dp(c, widthDp));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setAntiAlias(true);
        l.setInfoWindow(null);
        map.getOverlays().add(l);
    }

    private void dot(double lat, double lon, String label, int color, Runnable onTap) {
        Marker m = new Marker(map);
        m.setPosition(new GeoPoint(lat, lon));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setIcon(badge(label, color));
        m.setInfoWindow(null);
        m.setOnMarkerClickListener((marker, mv) -> {
            if (onTap != null) onTap.run();
            return true;
        });
        map.getOverlays().add(m);
    }

    /** A white-edged coloured circle with a number or letter in it. */
    private Drawable badge(String label, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p.setTextSize(Ui.dp(c, 12));
        int h = Ui.dp(c, label.isEmpty() ? 18 : 26);
        int w = label.length() <= 1 ? h : Math.max(h, Math.round(p.measureText(label)) + Ui.dp(c, 16));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float edge = Ui.dp(c, 2.5f);
        p.setColor(Color.WHITE);
        cv.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, p);
        p.setColor(color);
        cv.drawRoundRect(edge, edge, w - edge, h - edge, h / 2f - edge, h / 2f - edge, p);
        if (!label.isEmpty()) {
            p.setColor(Color.WHITE);
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(label, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2, p);
        }
        return new BitmapDrawable(c.getResources(), b);
    }

    /** Shows the area {minLat, minLon, maxLat, maxLon}, once the map knows its size. */
    void fit(double[] b) {
        if (b == null) return;
        double padLat = Math.max(0.0015, (b[2] - b[0]) * 0.12), padLon = Math.max(0.0015, (b[3] - b[1]) * 0.12);
        box = new double[]{b[0] - padLat, b[1] - padLon, b[2] + padLat, b[3] + padLon};
        Runnable go = () -> {
            BoundingBox bb = new BoundingBox(box[2], box[3], box[0], box[1]);
            map.zoomToBoundingBox(bb, false, Ui.dp(c, 12));
        };
        if (map.getWidth() > 0 && map.getHeight() > 0) go.run();
        else map.addOnFirstLayoutListener((v, l, t, r, bt) -> go.run());
    }

    void resume() {
        map.onResume();
    }

    void pause() {
        map.onPause();
    }

    void close() {
        map.onDetach();
    }
}
