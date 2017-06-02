package nl.hva.viewradar.utils;

import android.content.Context;
import android.content.SharedPreferences;

import nl.hva.viewradar.application.App;

public class PreferenceUtils {

    private static final String KEY = "sharedPreferences";
    private static final String KEY_FIRST_RUN = "first_run";
    private static final String KEY_SHOW_CAMERA = "show_camera";
    private static final String KEY_SENSOR_RANGE = "sensor_range";

    private static PreferenceUtils sInstance = new PreferenceUtils();

    private SharedPreferences mSharedPreferences;

    private PreferenceUtils() {
        mSharedPreferences = App.getContext().getSharedPreferences(KEY, Context.MODE_PRIVATE);
    }

    public static synchronized PreferenceUtils getInstance() {
        return sInstance;
    }

    /**
     * Set did first run.
     */
    public void firstRun(Boolean signin) {
        mSharedPreferences.edit()
                .putBoolean(KEY_FIRST_RUN, signin)
                .apply();
    }

    public boolean isFirstRun() {
        return mSharedPreferences.getBoolean(KEY_FIRST_RUN, true);
    }

    /**
     * If user checks 'show camera'.
     */
    public void showCamera(Boolean onoff) {
        mSharedPreferences.edit()
                .putBoolean(KEY_SHOW_CAMERA, onoff)
                .apply();
    }

    public boolean cameraOn() {
        return mSharedPreferences.getBoolean(KEY_SHOW_CAMERA, true);
    }

    /**
     * Save sensor range.
     */
    public void setRange(int range) {
        mSharedPreferences.edit()
                .putInt(KEY_SENSOR_RANGE, range)
                .apply();
    }

    public int getRange() {
        return mSharedPreferences.getInt(KEY_SENSOR_RANGE, 60);
    }

}