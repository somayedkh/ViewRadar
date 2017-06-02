package nl.hva.viewradar.adapters;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Handler;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.util.Log;
import android.view.View;

import nl.hva.viewradar.R;

import nl.hva.viewradar.fragments.ActionFragment;
import nl.hva.viewradar.fragments.CameraFragment;

public class MenuAdapter extends FragmentGridPagerAdapter {

    private final Context mContext;
    private final Handler mHandler;
    public final CameraFragment mCameraFragment;

    public static final int MESSAGE_SNAP = 1;
    public static final int MESSAGE_SWITCH = 2;

    private static int currentCamera = 0;
    int[] currentCameraText = { R.string.action_switch_0, R.string.action_switch_1 };
    int[] currentCameraIcon = { R.drawable.action_switch_0, R.drawable.action_switch_1 };

    public MenuAdapter(Context ctx, FragmentManager fm, Handler h) {
        super(fm);
        mContext = ctx;
        mHandler = h;
        mCameraFragment = new CameraFragment();
        mCameraFragment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("blah", "clicked snap");
                mHandler.obtainMessage(MESSAGE_SNAP).sendToTarget();
            }
        });
    }

    @Override
    public int getColumnCount(int arg0) {
        return 2;
    }

    @Override
    public int getRowCount() {
        return 1;
    }

    @Override
    public Fragment getFragment(int rowNum, int colNum) {
        Log.d("blah", String.format("getFragment(%d, %d)", rowNum, colNum));

        if(colNum == 0) {
            return mCameraFragment;
        }

        if(colNum == 1) {
            final ActionFragment switchAction = ActionFragment.newInstance(currentCameraIcon[currentCamera], currentCameraText[currentCamera]);
            switchAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("blah", "clicked switch");
                    currentCamera = ( currentCamera + 1 ) % currentCameraText.length;
                    switchAction.setTextRes(currentCameraText[currentCamera]);
                    switchAction.setIconRes(currentCameraIcon[currentCamera]);
                    mHandler.obtainMessage(MESSAGE_SWITCH, currentCamera, -1).sendToTarget();
                }
            });
            return switchAction;
        }

        return null;
    }

}
