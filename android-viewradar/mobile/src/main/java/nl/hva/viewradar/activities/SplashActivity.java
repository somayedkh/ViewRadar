package nl.hva.viewradar.activities;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;

import nl.hva.viewradar.R;

public class SplashActivity extends Activity {

    // The amount of milliseconds that the splash screen will be active.
    private static final int DURATION = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setContentView(R.layout.activity_splash);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startMainActivity();
            }
        }, DURATION);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivity(intent);
        finish();
    }

}
