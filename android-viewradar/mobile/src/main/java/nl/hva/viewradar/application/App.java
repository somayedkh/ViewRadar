package nl.hva.viewradar.application;

import android.app.Application;
import android.content.Context;

/**
 * The application class
 */
public class App extends Application {

    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
    }

    public static Context getContext() {
        return sContext;
    }
}
