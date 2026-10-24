package com.veryyl.studywage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TreeMap;

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

    // Pay rules, kept identical to earnings() in assets/index.html:
    //   +10% on the part of a day beyond 3 hours
    //   +20% on the whole day, for every day from the 10th day in a row of 30+ minutes, while the streak lasts
    static final double BIG_BONUS = 0.10, BIG_DAY_H = 3, STREAK_BONUS = 0.20, STREAK_MIN_H = 0.5;
    static final int STREAK_DAYS = 10;

    static double earned(JSONObject s) {
        JSONArray sessions = s.optJSONArray("sessions");
        if (sessions == null) return 0;
        double rate = s.optDouble("rate", 1);
        if (!(rate > 0)) rate = 1;
        ZoneId zone = ZoneId.systemDefault();
        TreeMap<LocalDate, Double> days = new TreeMap<>();
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject o = sessions.optJSONObject(i);
            if (o == null) continue;
            Instant at;
            try {
                at = Instant.parse(o.optString("at", ""));
            } catch (Exception e) {
                continue;
            }
            LocalDate d = at.atZone(zone).toLocalDate();
            Double prev = days.get(d);
            days.put(d, (prev == null ? 0 : prev) + o.optDouble("h", 0));
        }
        if (days.isEmpty()) return 0;
        double total = 0;
        int run = 0;
        for (LocalDate d = days.firstKey(); !d.isAfter(days.lastKey()); d = d.plusDays(1)) {
            Double hv = days.get(d);
            double h = hv == null ? 0 : hv;
            run = h >= STREAK_MIN_H ? run + 1 : 0;
            if (h <= 0) continue;
            double streak = run >= STREAK_DAYS ? STREAK_BONUS : 0;
            total += r2(rate * (h * (1 + streak) + Math.max(0, h - BIG_DAY_H) * BIG_BONUS));
        }
        return r2(total);
    }

    /** {hours, earned, moved, available} */
    static double[] totals(JSONObject s) {
        if (s == null) return new double[]{0, 0, 0, 0};
        double hours = sum(s.optJSONArray("sessions"), "h");
        double earned = earned(s);
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
