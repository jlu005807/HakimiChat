package com.example.hakimichat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.DrawableRes;

import java.io.InputStream;

/**
 * Very small GIF player view using android.graphics.Movie for simplicity.
 * Note: Movie is deprecated but still works for lightweight GIF playback.
 */
public class GifView extends View {
    private Movie movie;
    private long movieStart;

    public GifView(Context context) {
        super(context);
    }

    public GifView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGifResource(@DrawableRes int resId) {
        try {
            android.util.Log.d("GifView", "setGifResource resId=" + resId);
            InputStream is = getResources().openRawResource(resId);
            movie = Movie.decodeStream(is);
            if (movie == null) {
                android.util.Log.w("GifView", "Movie.decodeStream returned null for resId=" + resId);
            }
            requestLayout();
            invalidate();
        } catch (Exception ignored) {
            android.util.Log.w("GifView", "failed to load gif resource=" + resId, ignored);
            movie = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (movie != null) {
            long now = android.os.SystemClock.uptimeMillis();
            if (movieStart == 0) movieStart = now;
            int relTime = (int) ((now - movieStart) % movie.duration());
            movie.setTime(relTime);
            float scaleX = (float) getWidth() / (float) movie.width();
            float scaleY = (float) getHeight() / (float) movie.height();
            canvas.save();
            canvas.scale(Math.min(scaleX, scaleY), Math.min(scaleX, scaleY));
            movie.draw(canvas, 0, 0);
            canvas.restore();
            invalidate();
        }
    }
}
