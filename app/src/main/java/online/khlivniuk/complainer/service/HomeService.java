package online.khlivniuk.complainer.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ImageReader;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.List;

import online.khlivniuk.complainer.CameraHandler;
import online.khlivniuk.complainer.ImagePreprocessor;
import online.khlivniuk.complainer.classifier.Recognition;
import online.khlivniuk.complainer.classifier.TensorFlowHelper;

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
    private static final String LABELS_FILE = "labels.txt";
    private static final String MODEL_FILE = "mobilenet_quant_v1_224.tflite";
    private static final String TAG = HomeService.class.getSimpleName();
    private final IBinder mBinder = new HomeBinder();
    private boolean mProcessing;
    private Interpreter mTensorFlowLite;
    private List<String> mLabels;
    private CameraHandler mCameraHandler;
    private ImagePreprocessor mImagePreprocessor;
    private HomeEventsListener mListener;
    private Bitmap mBitmap;

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
            mTensorFlowLite = new Interpreter(TensorFlowHelper.loadModelFile(this, MODEL_FILE));
            mLabels = TensorFlowHelper.readLabels(this, LABELS_FILE);
        } catch (IOException e) {
            Log.w(TAG, "Unable to initialize TensorFlow Lite.", e);
        }
    }




    private void doRecognize(Bitmap image) {
        // Allocate space for the inference results
        byte[][] confidencePerLabel = new byte[1][mLabels.size()];
        // Allocate buffer for image pixels.
        int[] intValues = new int[TF_INPUT_IMAGE_WIDTH * TF_INPUT_IMAGE_HEIGHT];
        ByteBuffer imgData = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE * TF_INPUT_IMAGE_WIDTH * TF_INPUT_IMAGE_HEIGHT * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());

        // Read image data into buffer formatted for the TensorFlow model
        TensorFlowHelper.convertBitmapToByteBuffer(image, intValues, imgData);

        // Run inference on the network with the image bytes in imgData as input,
        // storing results on the confidencePerLabel array.
        mTensorFlowLite.run(imgData, confidencePerLabel);

        // Get the results with the highest confidence and map them to their labels
        Collection<Recognition> results = TensorFlowHelper.getBestResults(confidencePerLabel, mLabels);
        // Report the results with the highest confidence
        onPhotoRecognitionReady(results);
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

    private void onPhotoRecognitionReady(Collection<Recognition> results) {
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

