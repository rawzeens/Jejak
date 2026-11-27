package com.r4xn.jejak.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A place the person has named, such as Home or Office. Stops inside its circle get its name. */
public final class Place {
    public String id;
    public String name;
    public double lat, lon;
    /** Metres. */
    public double radius = 100;

    public Place(String id, String name, double lat, double lon, double radius) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.radius = radius;
    }

    /** The closest place whose circle holds the point, or null. */
    public static Place nearest(List<Place> places, double lat, double lon) {
        Place best = null;
        double bestD = Double.MAX_VALUE;
        for (Place p : places) {
            double d = Geo.distance(lat, lon, p.lat, p.lon);
            if (d <= p.radius && d < bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    public static String newId(List<Place> places) {
        int n = 1;
        for (Place p : places) {
            if (p.id.startsWith("place-")) {
                try {
                    n = Math.max(n, Integer.parseInt(p.id.substring(6)) + 1);
                } catch (NumberFormatException ignored) {
                    // someone else's id: keep counting
                }
            }
        }
        return "place-" + n;
    }

    public static String toJson(List<Place> places) {
        JSONArray a = new JSONArray();
        try {
            for (Place p : places) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("lat", p.lat);
                o.put("lon", p.lon);
                o.put("radius", p.radius);
                a.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return a.toString();
    }

    public static List<Place> fromJson(String json) {
        List<Place> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Place(o.getString("id"), o.optString("name", "Place"), o.getDouble("lat"), o.getDouble("lon"),
                        o.optDouble("radius", 100)));
            }
        } catch (JSONException e) {
            // a damaged list is treated as empty rather than stopping the app
        }
        return out;
    }
}
