package com.veryyl.studywage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Reads and writes the same state JSON the page (assets/index.html) keeps, and derives the totals. */
final class State {
    static final String PREFS = "study_wage";
    static final String KEY = "state";

    private static final DateTimeFormatter ISO_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC);

    private State() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static JSONObject load(Context ctx) {
        String raw = prefs(ctx).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    static void save(Context ctx, JSONObject s) {
        prefs(ctx).edit().putString(KEY, s.toString()).commit();
    }

    /** Same shape as JavaScript's Date.toISOString(). */
    static String iso(Instant t) {
        return ISO_MS.format(t);
    }

    static double r2(double n) {
        return Math.round(n * 100.0) / 100.0;
    }

    static double sum(JSONArray arr, String field) {
        double total = 0;
        if (arr == null) return 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) total += o.optDouble(field, 0);
        }
        return total;
    }

    /** {hours, earned, moved, available} */
    static double[] totals(JSONObject s) {
        if (s == null) return new double[]{0, 0, 0, 0};
        JSONArray sessions = s.optJSONArray("sessions");
        double hours = sum(sessions, "h");
        double earned = r2(sum(sessions, "amt"));
        double moved = r2(sum(s.optJSONArray("transfers"), "amt"));
        return new double[]{hours, earned, moved, Math.max(0, r2(earned - moved))};
    }

    /** Matches the page's hour format: "45 min", "2 h", "3.5 h", "0 h". */
    static String hours(double h) {
        if (h <= 0.004) return "0 h";
        if (h < 1) return Math.max(1, Math.round(h * 60)) + " min";
        double v = Math.round(h * 10) / 10.0;
        if (v == Math.floor(v)) return ((long) v) + " h";
        return v + " h";
    }

    static String money(double n) {
        return String.format(Locale.US, "$%.2f", Math.abs(n));
    }
}
