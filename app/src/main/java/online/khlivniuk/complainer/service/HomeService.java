package online.khlivniuk.complainer.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ImageReader;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.util.List;

import online.khlivniuk.complainer.CameraHandler;
import online.khlivniuk.complainer.ImagePreprocessor;
import online.khlivniuk.complainer.R;
import online.khlivniuk.complainer.classifier.ImageClassifier;
import online.khlivniuk.complainer.classifier.ImageClassifierFloatInception;

public class HomeService extends Service {
    public static final String ACTION_TAKESHOT = "online.khlivniuk.complainer.ACTION_TAKESHOT";
    // adb shell am startservice -n "online.khlivniuk.complainer/.service.HomeService" -a "online.khlivniuk.complainer.ACTION_TAKESHOT"
    /**
     * Camera image capture size
     */
    private static final int PREVIEW_IMAGE_WIDTH = 640;
    private static final int PREVIEW_IMAGE_HEIGHT = 480;
    /**
     * Image dimensions required by TF model
     */
    private static final int TF_INPUT_IMAGE_WIDTH = 224;
    private static final int TF_INPUT_IMAGE_HEIGHT = 224;
    /**
     * Dimensions of model inputs.
     */
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    /**
     * TF model asset files
     */
    private static final String LABELS_FILE = "retrained_labels.txt";
    private static final String MODEL_FILE = "optimized_graph.lite";
    private static final String TAG = HomeService.class.getSimpleName();
    private final IBinder mBinder = new HomeBinder();
    private boolean mProcessing;
    private Interpreter mTensorFlowLite;
    private List<String> mLabels;
    private CameraHandler mCameraHandler;
    private ImagePreprocessor mImagePreprocessor;
    private HomeEventsListener mListener;
    private Bitmap mBitmap;
    private ImageClassifier classifier;

    private void destroyClassifier() {
        mTensorFlowLite.close();
    }

    private void closeCamera() {
        mCameraHandler.shutDown();
    }

    private void loadPhoto() {
        mCameraHandler.takePicture();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initCamera();
        initClassifier();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_TAKESHOT.equals(intent.getAction())) {
            Log.d(TAG, "Got action");
            imageRequest();
        }
        return Service.START_STICKY;
    }

    private void initCamera() {
        mImagePreprocessor = new ImagePreprocessor(PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT,
                TF_INPUT_IMAGE_WIDTH, TF_INPUT_IMAGE_HEIGHT);
        mCameraHandler = CameraHandler.getInstance();
        mCameraHandler.initializeCamera(this,
                PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT, null,
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        Bitmap bitmap = mImagePreprocessor.preprocessImage(imageReader.acquireNextImage());
                        onPhotoReady(bitmap);
                    }
                });
    }

    private void initClassifier() {
        try {
            classifier = new ImageClassifierFloatInception(this);
        } catch (IOException e) {
            Log.d(TAG, "Classifier can't be loaded",e);
            e.printStackTrace();
        }
    }


    private void doRecognize(Bitmap image) {
        SpannableStringBuilder txtToShow = new SpannableStringBuilder();
        classifier.classifyFrame(image, txtToShow);
        // Report the results with the highest confidence
        Log.d(TAG, "RESULT:" + txtToShow.toString());

        onPhotoRecognitionReady(txtToShow.toString());
    }


    public boolean imageRequest() {
        if (mProcessing) {
            Log.d(TAG, "Still processing, please wait");
            return true;
        }
        Log.d(TAG, "Running photo recognition");
        mProcessing = true;
        loadPhoto();
        return true;
    }

    private void onPhotoReady(Bitmap bitmap) {
        mBitmap = bitmap;
        doRecognize(bitmap);
    }

    private void onPhotoRecognitionReady(String results) {
        Log.d(TAG, "onPhotoRecognitionReady");
        if (mListener != null) {
            mListener.photoProcessed(mBitmap, results);
        }
        mProcessing = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            destroyClassifier();
        } catch (Throwable t) {
            // close quietly
        }
        try {
            closeCamera();
        } catch (Throwable t) {
            // close quietly
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void registerListener(HomeEventsListener listener) {
        mListener = listener;
    }

    public class HomeBinder extends Binder {
        public HomeService getService() {
            return HomeService.this;
        }
    }

}

