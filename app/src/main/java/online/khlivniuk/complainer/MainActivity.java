package online.khlivniuk.complainer;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.Iterator;

import online.khlivniuk.complainer.classifier.Recognition;
import online.khlivniuk.complainer.service.HomeEventsListener;
import online.khlivniuk.complainer.service.HomeService;


public class MainActivity extends Activity implements HomeEventsListener {
    private static final String TAG = "ImageClassifierActivity";
    //adb shell am start -a "online.khlivniuk.complainer.ACTION_TAKESHOT"
    //adb shell am start -n "online.khlivniuk.complainer/.MainActivity" -a "online.khlivniuk.complainer.ACTION_TAKESHOT"

    private ImageView mImage;
    private TextView mResultText;
    private HomeService mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Service connected");
            HomeService.HomeBinder binder = (HomeService.HomeBinder) iBinder;
            mService = binder.getService();
            mService.registerListener(MainActivity.this);
            mService.imageRequest();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "Service disconnected");

        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        mImage = findViewById(R.id.imageView);
        mResultText = findViewById(R.id.resultText);
        bindService(new Intent(this, HomeService.class), mServiceConnection, Service.BIND_AUTO_CREATE);
    }

    private void updateStatus(String status) {
        Log.d(TAG, status);
        mResultText.setText(status);
    }

    @Override
    public void photoProcessed(Bitmap resultBitmap, Collection<Recognition> resultString) {
        mImage.setImageBitmap(resultBitmap);
        mResultText.setText(formatResults(resultString));
    }

    private String formatResults(Collection<Recognition> results) {
        if (results == null || results.isEmpty()) {
            return getString(R.string.empty_result);
        } else {
            StringBuilder sb = new StringBuilder();
            Iterator<Recognition> it = results.iterator();
            int counter = 0;
            while (it.hasNext()) {
                Recognition r = it.next();
                sb.append(r.getTitle());
                counter++;
                if (counter < results.size() - 1) {
                    sb.append(", ");
                } else if (counter == results.size() - 1) {
                    sb.append(" or ");
                }
            }
            return sb.toString();
        }
    }

}
