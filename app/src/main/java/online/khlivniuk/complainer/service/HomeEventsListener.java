package online.khlivniuk.complainer.service;

import android.graphics.Bitmap;

public interface HomeEventsListener {
    void photoProcessed(Bitmap resultBitmap, String resultString);
}
