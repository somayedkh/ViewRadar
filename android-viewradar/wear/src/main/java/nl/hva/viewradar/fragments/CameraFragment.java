package nl.hva.viewradar.fragments;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import nl.hva.viewradar.R;

import nl.hva.viewradar.activities.MainActivity;

import static android.content.Context.VIBRATOR_SERVICE;

public class CameraFragment extends Fragment {
    private View.OnClickListener mOnClickListener;

    public ImageView cameraPreview = null;
    public LinearLayout overlayLayout;
    public TextView overlayTitle;
    public TextView overlayClose;

    public Boolean showWarning = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.camera, container, false);
        cameraPreview = (ImageView) contentView.findViewById(R.id.camera_preview);
        overlayLayout = (LinearLayout) contentView.findViewById(R.id.overlay_layout);
        overlayTitle = (TextView) contentView.findViewById(R.id.overlay_title);
        overlayClose = (TextView) contentView.findViewById(R.id.overlay_close);
        cameraPreview.setOnClickListener(mOnClickListener);

        SharedPreferences preferences = getActivity().getPreferences(0);
        final Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    if (((MainActivity) getActivity()).isObjectDetected() && !showWarning) {
                        showWarning = true;
                        setWarningScreen();
                    }
                }
                h.postDelayed(this, 10);
            }
        }, 10);
//        if (preferences.getBoolean("tip0_hide", false) == false) {
//            mImageView_tip = (TextView) contentView.findViewById(R.id.tip);
//            //mImageView_tip.setImageResource(R.drawable.tip_0);
//            mImageView_tip.setVisibility(View.VISIBLE);
//            mImageView_tip.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    mImageView_tip.setVisibility(View.GONE);
//                    SharedPreferences preferences = getActivity().getPreferences(0);
//                    SharedPreferences.Editor editor = preferences.edit();
//                    editor.putBoolean("tip0_hide", true);
//                    editor.commit();
//                }
//            });
//        }

        return contentView;
    }

    public void setWarningScreen() {
        overlayTitle.setText("Pas op!");
        overlayTitle.setVisibility(View.VISIBLE);
        Vibrator vibrator = (Vibrator) getActivity().getSystemService(VIBRATOR_SERVICE);
        long[] vibrationPattern = {0, 1000, 50, 1000};
        //-1 - don't repeat
        final int indexInPatternToRepeat = -1;
        vibrator.vibrate(vibrationPattern, indexInPatternToRepeat);
        ((MainActivity) getActivity()).setObjectDetected(false);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                overlayTitle.setVisibility(View.GONE);
                overlayClose.setVisibility(View.VISIBLE);
                showWarning = false;
                if (!((MainActivity) getActivity()).getCameraStatus()) {
                    // Gets the layout params that will allow you to resize the layout
                    ViewGroup.LayoutParams params = overlayTitle.getLayoutParams();
                    // Changes the height and width to the specified *pixels*
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    overlayTitle.setLayoutParams(params);
                }
            }
        }, 10000);
    }

    public void setOnClickListener(View.OnClickListener listener) {
        mOnClickListener = listener;
    }
}