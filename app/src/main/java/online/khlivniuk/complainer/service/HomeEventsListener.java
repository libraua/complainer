package online.khlivniuk.complainer.service;

import android.graphics.Bitmap;

import java.util.Collection;

import online.khlivniuk.complainer.classifier.Recognition;

public interface HomeEventsListener {
    void photoProcessed(Bitmap resultBitmap, Collection<Recognition> resultString);
}
