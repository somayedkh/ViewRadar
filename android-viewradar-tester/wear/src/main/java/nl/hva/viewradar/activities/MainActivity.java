package nl.hva.viewradar.activities;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.wearable.view.GridViewPager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import nl.hva.viewradar.R;

import java.util.Scanner;
import java.util.Timer;

import nl.hva.viewradar.adapters.MenuAdapter;

public class MainActivity extends Activity {

    private static final String TAG = "WearCamera";
    private static final boolean D = true;
    private final MainActivity self = this;

    private GoogleApiClient mGoogleApiClient = null;
    private Node mPhoneNode = null;

    private MenuAdapter mMenuAdapter;

    private Vibrator mVibrator;

    GridViewPager mGridViewPager;

    private boolean mPreviewRunning = true;

    private static int currentTimer = 0;
    private int frameNumber = 0;
    private boolean timerIsRunning = false;
    private boolean objectDetected = false;
    private boolean cameraOn = false;

    private Timer mTimer;

    int selfTimerSeconds;

    private MessageApi.MessageListener mMessageListener = new MessageApi.MessageListener() {
        @Override
        public void onMessageReceived (MessageEvent m){
            Scanner s = new Scanner(m.getPath());
            String command = s.next();
            switch (command) {
                case "stop":
                    mPreviewRunning = false;
                    moveTaskToBack(true);
                    break;
                case "start":
                    mPreviewRunning = true;
                    break;
                case "show":
                    cameraOn = true;
                    byte[] data = m.getData();
                    Bitmap bmpSmall = BitmapFactory.decodeByteArray(data, 0, data.length);
                    setBitmap(bmpSmall);
                    if (mPhoneNode != null && s.hasNextLong() && (frameNumber++ % 8) == 0) {
                        sendToPhone(String.format("received %d", s.nextLong()), null, null);
                    }
                    break;
                case "result":
                    if (D) Log.d(TAG, "result");
                    onMessageResult(m.getData());
                    break;
            }

        }
    };

    void findPhoneNode() {
        PendingResult<NodeApi.GetConnectedNodesResult> pending = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        pending.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if(result.getNodes().size()>0) {
                    mPhoneNode = result.getNodes().get(0);
                    if(D) Log.d(TAG, "Found wearable: name=" + mPhoneNode.getDisplayName() + ", id=" + mPhoneNode.getId());
                    sendToPhone("start", null, null);
                    doSwitch(currentCamera);
                    //doFlash(currentFlash);
                } else {
                    mPhoneNode = null;
                }
            }
        });
    }

    public boolean getCameraStatus() {
        return cameraOn;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPreviewRunning = false;
        if(mPhoneNode != null) {
            sendToPhone("stop", null, new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                    }
                    moveTaskToBack(true);
                }
            });
        } else {
            findPhoneNode();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mPhoneNode != null) {
            sendToPhone("stop", null, new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                    }
                    moveTaskToBack(true);
                }
            });
        }
        Wearable.MessageApi.removeListener(mGoogleApiClient, mMessageListener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setGoogleApiClient();

    }

    public void initViews() {
        mMenuAdapter = new MenuAdapter(this, getFragmentManager(), mHandler);
        mGridViewPager = (GridViewPager) findViewById(R.id.pager);
        mGridViewPager.setAdapter(mMenuAdapter);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        findPhoneNode();
                        Wearable.MessageApi.addListener(mGoogleApiClient, mMessageListener);
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mPhoneNode != null) {
            //sendToPhone("stop", null, null);
        } else {
            findPhoneNode();
        }
        mPreviewRunning = false;
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        if(mPhoneNode != null) {
            sendToPhone("start", null, null);
            //doSwitch(currentCamera);
        } else {
            findPhoneNode();
        }
        mPreviewRunning = true;
        super.onResume();
    }

    public void setBitmap(final Bitmap bmp) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mMenuAdapter.mCameraFragment.cameraPreview != null) {
                        BitmapDrawable drawable = ((BitmapDrawable) mMenuAdapter.mCameraFragment.cameraPreview.getDrawable());
                        if (drawable instanceof BitmapDrawable) {
                            drawable.getBitmap().recycle();
                        }
                        mMenuAdapter.mCameraFragment.cameraPreview.setImageBitmap(bmp);
                    }
                }
            });
    }

    private static int currentCamera = 0;

    private void doSwitch(int arg0) {
        currentCamera = arg0;
        sendToPhone("switch " + String.valueOf(arg0), null, null);
    }

    private void sendToPhone(String path, byte[] data, final ResultCallback<MessageApi.SendMessageResult> callback) {
        if (mPhoneNode != null) {
            PendingResult<MessageApi.SendMessageResult> pending = Wearable.MessageApi.sendMessage(mGoogleApiClient, mPhoneNode.getId(), path, data);
            pending.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult result) {
                    if (callback != null) {
                        callback.onResult(result);
                    }
                    if (!result.getStatus().isSuccess()) {
                        if(D) Log.d(TAG, "ERROR: failed to send Message: " + result.getStatus());
                    }
                }
            });
        } else {
            if(D) Log.d(TAG, "ERROR: tried to send message before device was found");
        }
    }
    
    private void onMessageResult(final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (data.length > 1) {
//                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
//                    mMenuAdapter.mCameraFragment.cameraResult.setImageBitmap(bmp);
//                    mMenuAdapter.mCameraFragment.cameraResult.setTranslationX(0);
//                    mMenuAdapter.mCameraFragment.cameraResult.setRotation(0);
//                    mMenuAdapter.mCameraFragment.cameraResult.setVisibility(View.VISIBLE);
//                    mMenuAdapter.mCameraFragment.cameraResult.animate().setDuration(500).translationX(mMenuAdapter.mCameraFragment.cameraPreview.getWidth()).rotation(40).withEndAction(new Runnable() {
//                        public void run() {
//                            mMenuAdapter.mCameraFragment.cameraResult.setVisibility(View.GONE);
//                        }
//                    });
                } else {
                    objectDetected = true;
                }
            }
        });
    }

    public boolean isObjectDetected() {
        Log.d("Watch -isObjectDetected", String.valueOf(objectDetected));
        return objectDetected;
    }

    public boolean setObjectDetected(boolean pDetected) {
        Log.d("Watch setObjectDetected", String.valueOf(pDetected));
        return objectDetected = pDetected;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MenuAdapter.MESSAGE_SWITCH:
                    Log.d(TAG, "MESSAGE_SWITCH");
                    doSwitch(msg.arg1);
                    mGridViewPager.scrollTo(0, 0);
                    break;
            }
        }
    };
}
