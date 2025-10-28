package com.example.linktocomputer.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ConfirmSeekBar extends androidx.appcompat.widget.AppCompatSeekBar {
    private int lastProgress;
    public ConfirmSeekBar(Context context) {
        super(context);
    }
    public ConfirmSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                super.onTouchEvent(event);
                if(getProgress()>=6) {
                    setProgress(0);
                    lastProgress=0;
                    return true;
                }
                lastProgress=getProgress();
                break;
            case MotionEvent.ACTION_MOVE:
                int progress=getProgress();
                if(Math.abs(lastProgress-progress)<=4){
                    super.onTouchEvent(event);
                    lastProgress=progress;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                setProgress(0);
                lastProgress=0;
                break;

        }
        return true;
    }
}
