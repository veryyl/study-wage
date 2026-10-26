package com.veryyl.studywage;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** A one-row home-screen bar: hours today and this week, today's pay, and the current streak. */
public class StudyWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.veryyl.studywage.WIDGET_REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        renderAll(ctx, null);
        syncInBackground(ctx, false);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            renderAll(ctx, "SYNCING…");
            syncInBackground(ctx, true);
        }
    }

    private void syncInBackground(final Context ctx, final boolean manual) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            String status = null;
            try {
                int r = TogglSync.run(ctx.getApplicationContext(), manual);
                if (manual) {
                    if (r == TogglSync.QUOTA) status = "LIMIT HIT";
                    else if (r == TogglSync.FAILED) status = "OFFLINE";
                }
                renderAll(ctx, status);
            } finally {
                pending.finish();
            }
        }).start();
    }

    /** Redraw every placed widget from the saved state. Safe to call from any thread. */
    static void renderAll(Context ctx, String status) {
        Context app = ctx.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, StudyWidget.class));
        if (ids == null || ids.length == 0) return;

        State.Summary sum = State.summary(State.load(app));

        RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_study);
        v.setTextViewText(R.id.w_today, State.hours(sum.hoursToday));
        v.setTextViewText(R.id.w_week, State.hours(sum.hoursWeek));
        v.setTextViewText(R.id.w_pay, State.money(sum.earnedToday));
        v.setTextViewText(R.id.w_streak, sum.streak == 1 ? "1 day" : sum.streak + " days");
        v.setTextViewText(R.id.w_today_label, status != null ? status : "TODAY");

        Intent open = new Intent(app, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(app, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent refresh = new Intent(app, StudyWidget.class);
        refresh.setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.w_refresh, PendingIntent.getBroadcast(app, 1, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        mgr.updateAppWidget(ids, v);
    }
}
