package nl.hva.viewradar;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import net.dheera.viewradar.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "WearCamera";
    private static final boolean D = true;
    private int displayFrameLag = 0;
    private long lastMessageTime = 0;
    private long displayTimeLag = 0;

    private SurfaceHolder mSurfaceHolder;
    private SurfaceView mSurfaceView;
    private ImageView mImageView;
    private TextView mTextview;
    private Camera mCamera;
    public int mCameraOrientation;
    public boolean mPreviewRunning=false;
    private GoogleApiClient mGoogleApiClient;
    private Node mWearableNode = null;
    private boolean readyToProcessImage = true;

    private static int currentCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;

    //
    private boolean showList = true;
    Handler bluetoothIn;

    final int handlerState = 0;        				 //used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address
    private static String address;

    private MessageApi.MessageListener mMessageListener = new MessageApi.MessageListener() {
        @Override
        public void onMessageReceived (MessageEvent m){
            if(D) Log.d(TAG, "onMessageReceived: " + m.getPath());
            lastMessageTime = System.currentTimeMillis();
            Scanner s = new Scanner(m.getPath());
            String command = s.next();
            if (command.equals("switch")) {
                int arg0 = 0;
                if (s.hasNextInt()) arg0 = s.nextInt();
                doSwitch(arg0);
            } else if(command.equals("received")) {
                long arg0 = 0;
                if(s.hasNextLong()) arg0 = s.nextLong();
                displayTimeLag = System.currentTimeMillis() - arg0;
                if(D) Log.d(TAG, String.format("frame lag time: %d ms", displayTimeLag));
            } else if(command.equals("stop")) {
                moveTaskToBack(true);
            }
        }
    };

    void findWearableNode() {
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                if(result.getNodes().size()>0) {
                    mWearableNode = result.getNodes().get(0);
                    if(D) Log.d(TAG, "Found wearable: name=" + mWearableNode.getDisplayName() + ", id=" + mWearableNode.getId());
                } else {
                    mWearableNode = null;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if(D) Log.d(TAG, "onDestroy");
        Wearable.MessageApi.removeListener(mGoogleApiClient, mMessageListener);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(D) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Watch
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mTextview = (TextView) findViewById(R.id.textView);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle connectionHint) {
                            if(D) Log.d(TAG, "onConnected: " + connectionHint);
                            findWearableNode();
                            Wearable.MessageApi.addListener(mGoogleApiClient, mMessageListener);
                        }
                        @Override
                        public void onConnectionSuspended(int cause) {
                            if(D) Log.d(TAG, "onConnectionSuspended: " + cause);
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                            if(D) Log.d(TAG, "onConnectionFailed: " + result);
                        }
                    })
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();


        // slowly subtract from the lag; in case the lag
        // is occurring due to transmission errors
        // this will un-stick the application
        // from a stuck state in which displayFrameLag>6
        // and nothing gets transmitted (therefore nothing
        // else pulls down displayFrameLag to allow transmission
        // again)

        lastMessageTime = System.currentTimeMillis();

        Timer mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(displayFrameLag>1) { displayFrameLag--; }
                if(displayTimeLag>1000) { displayTimeLag-=1000; }
            }
        }, 0, 1000);

        //Arduino BT
        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {										//if message is what we want
                    String readMessage = (String) msg.obj;                                                                // msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage);//keep appending to string until ~
                    int totalLength = recDataString.length();
                    if (totalLength > 3) {
                        String newString = recDataString.substring(totalLength - 4, totalLength - 2);
                        mTextview.setText(newString);
                        if (Integer.parseInt(newString) < 50 && Integer.parseInt(newString) > 10) {
                                boolean[] detected = {true};
                                // add function to draw rects on view/surface/canvas
                                sendToWearable("start", toBytes(detected), null);
                                sendToWearable("result", toBytes(detected), null);
                        }
                    } else {
                        mTextview.setText(recDataString);
                    }
//                    int endOfLineIndex = recDataString.indexOf("\r\n");                    // determine the end-of-line
//                    if (endOfLineIndex > 0) {                                           // make sure there data before ~
//                        String dataInPrint = recDataString.substring(0, endOfLineIndex);    // extract string
//                        //txtString.setText("Data Received = " + dataInPrint);
//                        int dataLength = dataInPrint.length();							//get length of data received
//                        //txtStringLength.setText("String Length = " + String.valueOf(dataLength));
//                        Log.d("info", dataInPrint);
//                        if (recDataString.charAt(0) == '#')								//if it starts with # we know it is what we are looking for
//                        {
//                            String sensor0 = recDataString.substring(1, 5);             //get sensor value from string between indices 1-5
//                            String sensor1 = recDataString.substring(6, 10);            //same again...
//                            String sensor2 = recDataString.substring(11, 15);
//                            String sensor3 = recDataString.substring(16, 20);
//
//                        }
//                        recDataString.delete(0, recDataString.length()); 					//clear all string data
//                        // strIncom =" ";
//                        dataInPrint = " ";
//                    }
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();
    }

    public void setCameraDisplayOrientation() {
            Camera.CameraInfo info = new Camera.CameraInfo();
            mCamera.getCameraInfo(currentCamera, info);
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }
            int resultA = 0, resultB = 0;
            if(currentCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
                resultA = (info.orientation - degrees + 360) % 360;
                resultB = (info.orientation - degrees + 360) % 360;
                mCamera.setDisplayOrientation(resultA);
            } else {
                resultA = (360 + 360 - info.orientation - degrees) % 360;
                resultB = (info.orientation + degrees) % 360;
                mCamera.setDisplayOrientation(resultA);
            }
            Camera.Parameters params = mCamera.getParameters();
            params.setRotation(resultB);
            mCamera.setParameters(params);
            mCameraOrientation = resultB;
    }


//    public void doFlash(int arg0) {
//        if(arg0 == 0)
//            currentFlashMode = Camera.Parameters.FLASH_MODE_OFF;
//        else if(arg0 == 1)
//            currentFlashMode = Camera.Parameters.FLASH_MODE_AUTO;
//        else if(arg0 == 2)
//            currentFlashMode = Camera.Parameters.FLASH_MODE_ON;
//
//        if((mCamera != null) && mPreviewRunning) {
//            Camera.Parameters p = mCamera.getParameters();
//            p.setFlashMode(currentFlashMode);
//            mCamera.setParameters(p);
//        }
//    }

    public void doSwitch(int arg0) {
        Log.d(TAG, String.format("doSwitch(%d)", arg0));

        int oldCurrentCamera = currentCamera;

        if(Camera.getNumberOfCameras()>=2) {
            if(arg0 == 1) {
                currentCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
            } else {
                currentCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
            }
        } else {
            currentCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }

        if((oldCurrentCamera != currentCamera) && mPreviewRunning) {
            surfaceDestroyed(mSurfaceHolder);
            if (arg0 == 0) {
                surfaceCreated(mSurfaceHolder);
                surfaceChanged(mSurfaceHolder, 0, 0, 0);
            } else {
                surfaceCreated(mSurfaceHolder);
                surfaceChanged(mSurfaceHolder, 0, 0, 0);
            }
        }
    }

    private void sendToWearable(String path, byte[] data, final ResultCallback<MessageApi.SendMessageResult> callback) {
        if (mWearableNode != null) {
            PendingResult<MessageApi.SendMessageResult> pending = Wearable.MessageApi.sendMessage(mGoogleApiClient, mWearableNode.getId(), path, data);
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

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        lastMessageTime = System.currentTimeMillis();
        super.onResume();

        //Get MAC address from DeviceListActivity via intent
        Intent intent = getIntent();

        //Get the MAC address from the DeviceListActivty via EXTRA
        address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        //create device and set the MAC address
        if (address != null) {
            BluetoothDevice device = btAdapter.getRemoteDevice(address);


            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
            }
            // Establish the Bluetooth socket connection.
            try {
                btSocket.connect();
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    //insert code to deal with this
                }
            }
            mConnectedThread = new ConnectedThread(btSocket);
            mConnectedThread.start();

            //I send a character when resuming.beginning transmission to check device is connected
            //If it is not an exception will be thrown in the write method and finish() will be called
            mConnectedThread.write("x");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

        if (mPreviewRunning) {
            mCamera.stopPreview();
        }
        if (mSurfaceHolder.getSurface() == null){
            return;
        }
        Camera.Parameters p = mCamera.getParameters();
        mCamera.setParameters(p);
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(arg0);
                setCameraDisplayOrientation();
                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera arg1) {
                        if (mWearableNode != null && readyToProcessImage && mPreviewRunning && displayFrameLag<6 && displayTimeLag<2000
                                && System.currentTimeMillis() - lastMessageTime < 4000) {
                            readyToProcessImage = false;
                            try {
                                Camera.Size previewSize = mCamera.getParameters().getPreviewSize();

                                int[] rgb = decodeYUV420SP(data, previewSize.width, previewSize.height);
                                Bitmap bmp = Bitmap.createBitmap(rgb, previewSize.width, previewSize.height, Bitmap.Config.ARGB_8888);
                                int smallWidth, smallHeight;
                                int dimension = 200;
                                // stream is lagging, cut resolution and catch up
                                if(displayTimeLag > 1500) {
                                    dimension = 50;
                                } else if(displayTimeLag > 500) {
                                    dimension = 100;
                                } else {
                                    dimension = 200;
                                }
                                if(previewSize.width > previewSize.height) {
                                    smallWidth = dimension;
                                    smallHeight = dimension*previewSize.height/previewSize.width;
                                } else {
                                    smallHeight = dimension;
                                    smallWidth = dimension*previewSize.width/previewSize.height;
                                }

                                Matrix matrix = new Matrix();
                                matrix.postRotate(mCameraOrientation);

                                Bitmap bmpSmall = Bitmap.createScaledBitmap(bmp, smallWidth, smallHeight, false);
                                Bitmap bmpSmallRotated = Bitmap.createBitmap(bmpSmall, 0, 0, smallWidth, smallHeight, matrix, false);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bmpSmallRotated.compress(Bitmap.CompressFormat.WEBP, 30, baos);
                                displayFrameLag++;
                                sendToWearable(String.format("show %d", System.currentTimeMillis()), baos.toByteArray(), new ResultCallback<MessageApi.SendMessageResult>() {
                                    @Override
                                    public void onResult(MessageApi.SendMessageResult result) {
                                        if(displayFrameLag>0) displayFrameLag--;
                                    }
                                });
                                bmp.recycle();
                                bmpSmall.recycle();
                                bmpSmallRotated.recycle();
                                readyToProcessImage = true;
                            } catch (RuntimeExecutionException e) {
                                //Do nothing
                            }
                        }
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
        mCamera.startFaceDetection();
        mPreviewRunning = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open(currentCamera);
        MyFaceDetectionListener fDListener = new MyFaceDetectionListener();
        mCamera.setFaceDetectionListener(fDListener);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mPreviewRunning = false;
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public int[] decodeYUV420SP( byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        int rgb[]=new int[width*height];
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
                        | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    public byte[] toBytes(boolean[] input) {
        byte[] toReturn = new byte[input.length / 8];
        for (int entry = 0; entry < toReturn.length; entry++) {
            for (int bit = 0; bit < 8; bit++) {
                if (input[entry * 8 + bit]) {
                    toReturn[entry] |= (128 >> bit);
                }
            }
        }

        return toReturn;
    }

    public void onClickSwitch(View view) {
        doSwitch(currentCamera);
    }

    private class MyFaceDetectionListener implements Camera.FaceDetectionListener {

        @Override
        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
            if (faces.length == 0) {
                Log.i(TAG, "No faces detected");
            } else if (faces.length > 0) {
                Log.i(TAG, "Faces Detected = " +
                        String.valueOf(faces.length));
                boolean[] detected = {true};

                List<Rect> faceRects;
                faceRects = new ArrayList<Rect>();

                for (int i=0; i<faces.length; i++) {
                    int left = faces[i].rect.left;
                    int right = faces[i].rect.right;
                    int top = faces[i].rect.top;
                    int bottom = faces[i].rect.bottom;
                    Rect uRect = new Rect(left, top, right, bottom);
                    faceRects.add(uRect);
                }

                // add function to draw rects on view/surface/canvas
                //sendToWearable("start", toBytes(detected), null);
                //sendToWearable("result", toBytes(detected), null);
            }
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }

    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }


        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);        	//read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();

            }
        }
    }
}
