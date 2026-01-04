package com.example.linktocomputer.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ConfirmSeekBar extends androidx.appcompat.widget.AppCompatSeekBar {
    private boolean allowTracking;
    public ConfirmSeekBar(Context context) {
        super(context);
    }
    public ConfirmSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                allowTracking = isTouchInStartArea(event);
                if(!allowTracking) {
                    setPressed(false);
                    setProgress(0);
                    return true;
                }
                return super.onTouchEvent(event);
            }
            case MotionEvent.ACTION_MOVE: {
                if(!allowTracking) return true;
                return super.onTouchEvent(event);
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if(allowTracking) {
                    super.onTouchEvent(event);
                }
                setPressed(false);
                if(getProgress() < getMax()) {
                    setProgress(0);
                }
                allowTracking = false;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private boolean isTouchInStartArea(MotionEvent event) {
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if(availableWidth <= 0) return true;
        float x = event.getX() - getPaddingLeft();
        float fraction = x / availableWidth;
        return fraction <= 0.06f;
    }
}
