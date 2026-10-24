package com.veryyl.studywage;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Native copy of the page's Toggl sync, used by the widget so it can update
 * without opening the app. Same rules: finished entries only, the user's
 * filter and start date, one session per Toggl entry (keyed "toggl:<id>").
 */
final class TogglSync {
    static final String HOST = "https://api.track.toggl.com";
    static final int ADDED_NONE = 0;
    static final int SKIPPED = -1;
    static final int QUOTA = -2;
    static final int FAILED = -3;

    private static final Random RNG = new Random();

    private TogglSync() {
    }

    static final class HttpError extends Exception {
        final int code;

        HttpError(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    static String get(String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(HOST + path).openConnection();
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            String auth = Base64.encodeToString(
                    (token.trim() + ":api_token").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + auth);
            c.setRequestProperty("Content-Type", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new HttpError(code);
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "");
    }

    private static String uid() {
        String r = Long.toString(RNG.nextLong() & Long.MAX_VALUE, 36) + "00000";
        return Long.toString(System.currentTimeMillis(), 36) + r.substring(0, 5);
    }

    /**
     * @param manual true when the person tapped refresh: ignores the 10-minute gap and a quota pause
     * @return number of new sessions added, or SKIPPED / QUOTA / FAILED
     */
    static synchronized int run(Context ctx, boolean manual) {
        JSONObject s = State.load(ctx);
        if (s == null) return SKIPPED;
        JSONObject t = s.optJSONObject("toggl");
        if (t == null) return SKIPPED;
        String token = str(t, "token");
        if (token.isEmpty()) return SKIPPED;

        Instant now = Instant.now();
        Instant paused = parseInstant(str(t, "pausedUntil"));
        if (!manual && paused != null && now.isBefore(paused)) return SKIPPED;
        Instant last = parseInstant(str(t, "last"));
        if (!manual && last != null && now.isBefore(last.plus(10, ChronoUnit.MINUTES))) return SKIPPED;

        Instant from;
        try {
            from = LocalDate.parse(str(t, "from")).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            from = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        Instant start = from.isAfter(now.minus(89, ChronoUnit.DAYS)) ? from : now.minus(89, ChronoUnit.DAYS);
        Instant end = now.plus(1, ChronoUnit.DAYS);

        try {
            String path = "/api/v9/me/time_entries?start_date="
                    + URLEncoder.encode(start.truncatedTo(ChronoUnit.SECONDS).toString(), "UTF-8")
                    + "&end_date=" + URLEncoder.encode(end.truncatedTo(ChronoUnit.SECONDS).toString(), "UTF-8");
            JSONArray entries = new JSONArray(get(path, token));

            String filter = str(t, "filter").trim().toLowerCase(Locale.ROOT);
            JSONObject projects = t.optJSONObject("projects");
            if (projects == null) projects = new JSONObject();
            if (!filter.isEmpty()) {
                Instant projectsAt = parseInstant(str(t, "projectsAt"));
                boolean stale = projectsAt == null || now.isAfter(projectsAt.plus(1, ChronoUnit.DAYS));
                boolean unknown = false;
                for (int i = 0; i < entries.length() && !unknown; i++) {
                    JSONObject e = entries.optJSONObject(i);
                    if (e != null && !e.isNull("project_id") && !projects.has(String.valueOf(e.optLong("project_id")))) unknown = true;
                }
                if (stale || unknown) {
                    try {
                        JSONArray pa = new JSONArray(get("/api/v9/me/projects", token));
                        JSONObject fresh = new JSONObject();
                        for (int i = 0; i < pa.length(); i++) {
                            JSONObject p = pa.optJSONObject(i);
                            if (p != null) fresh.put(String.valueOf(p.optLong("id")), str(p, "name"));
                        }
                        projects = fresh;
                        t.put("projects", projects);
                        t.put("projectsAt", State.iso(now));
                    } catch (Exception ignored) {
                    }
                }
            }

            JSONArray sessions = s.optJSONArray("sessions");
            if (sessions == null) {
                sessions = new JSONArray();
                s.put("sessions", sessions);
            }
            Set<String> have = new HashSet<>();
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject o = sessions.optJSONObject(i);
                if (o != null && !o.isNull("key")) have.add(o.optString("key"));
            }
            double rate = s.optDouble("rate", 1);
            if (!(rate > 0)) rate = 1;

            int added = 0;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                double duration = e.optDouble("duration", -1);
                if (!(duration > 0) || e.isNull("stop") || (e.has("server_deleted_at") && !e.isNull("server_deleted_at"))) continue;
                String key = "toggl:" + e.optLong("id");
                if (have.contains(key)) continue;
                Instant startAt = parseInstant(str(e, "start"));
                if (startAt == null || startAt.isBefore(from)) continue;
                String proj = e.isNull("project_id") ? "" : projects.optString(String.valueOf(e.optLong("project_id")), "");
                StringBuilder tags = new StringBuilder();
                JSONArray ta = e.optJSONArray("tags");
                if (ta != null) for (int k = 0; k < ta.length(); k++) tags.append(ta.optString(k)).append(' ');
                String desc = str(e, "description");
                if (!filter.isEmpty() && !(proj + " " + tags + " " + desc).toLowerCase(Locale.ROOT).contains(filter)) continue;
                double h = State.r2(duration / 3600.0);
                if (!(h > 0)) continue;
                String note = !desc.isEmpty() ? desc : (!proj.isEmpty() ? proj : "Toggl entry");
                if (note.length() > 80) note = note.substring(0, 80);
                JSONObject session = new JSONObject();
                session.put("id", uid());
                session.put("at", State.iso(startAt));
                session.put("h", h);
                session.put("amt", State.r2(h * rate));
                session.put("src", "toggl");
                session.put("note", note);
                session.put("key", key);
                sessions.put(session);
                have.add(key);
                added++;
            }
            t.put("last", State.iso(now));
            t.put("pausedUntil", "");
            State.save(ctx, s);
            return added;
        } catch (HttpError e) {
            if (e.code == 402 || e.code == 429) {
                try {
                    t.put("pausedUntil", State.iso(now.plus(1, ChronoUnit.HOURS)));
                    State.save(ctx, s);
                } catch (Exception ignored) {
                }
                return QUOTA;
            }
            return FAILED;
        } catch (Exception e) {
            return FAILED;
        }
    }
}
