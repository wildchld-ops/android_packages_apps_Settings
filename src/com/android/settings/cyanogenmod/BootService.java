package com.android.settings.cyanogenmod;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;

import com.android.settings.ioap.weather.WeatherRefreshService;
import com.android.settings.ioap.weather.WeatherService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class BootService extends Service {

    public static boolean servicesStarted = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
        }
        new BootWorker(this).execute();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class BootWorker extends AsyncTask<Void, Void, Void> {

        Context c;

        public BootWorker(Context c) {
            this.c = c;
        }

        @Override
        protected Void doInBackground(Void... args) {

            if (Settings.System
                    .getBoolean(getContentResolver(), Settings.System.USE_WEATHER, false)) {
                sendLastWeatherBroadcast();
                getApplicationContext().startService(new Intent(c, WeatherRefreshService.class));
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            servicesStarted = true;
            stopSelf();
        }
    }

    private void sendLastWeatherBroadcast() {
        SharedPreferences settings =
                getApplicationContext().getSharedPreferences(WeatherService.PREFS_NAME, 0);

        Intent broadcast = new Intent(WeatherService.INTENT_WEATHER_UPDATE);
        try {
            broadcast.putExtra(WeatherService.EXTRA_CITY, settings.getString("city", ""));
            broadcast.putExtra(WeatherService.EXTRA_CONDITION, settings.getString("condition", ""));
            broadcast.putExtra(WeatherService.EXTRA_LAST_UPDATE,
                    settings.getString("timestamp", ""));
            broadcast.putExtra(WeatherService.EXTRA_CONDITION_CODE,
                    settings.getString("condition_code", ""));
            broadcast.putExtra(WeatherService.EXTRA_FORECAST_DATE,
                    settings.getString("forecast_date", ""));
            broadcast.putExtra(WeatherService.EXTRA_HUMIDITY, settings.getString("humidity", ""));
            broadcast.putExtra(WeatherService.EXTRA_TEMP, settings.getString("temp", ""));
            broadcast.putExtra(WeatherService.EXTRA_WIND, settings.getString("wind", ""));
            broadcast.putExtra(WeatherService.EXTRA_LOW, settings.getString("low", ""));
            broadcast.putExtra(WeatherService.EXTRA_HIGH, settings.getString("high", ""));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        getApplicationContext().sendBroadcast(broadcast);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}