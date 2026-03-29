package com.suisho.linktocomputer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class CodeScannerOverlay extends View {
    private final Paint scannerFramePaint=new Paint();
    private int parentSheetState=BottomSheetBehavior.STATE_COLLAPSED;
    public CodeScannerOverlay(Context context) {
        super(context);
        init();
    }

    public CodeScannerOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init(){
        scannerFramePaint.setColor(Color.RED);
        scannerFramePaint.setStrokeWidth(5);
        scannerFramePaint.setStyle(Paint.Style.STROKE);
    }
    public void setParentSheetDialog(BottomSheetDialog parentSheetDialog){
        parentSheetDialog.getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED||newState == BottomSheetBehavior.STATE_EXPANDED) {
                    parentSheetState=newState;
                    invalidate();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });
    }
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float frameSize = viewHeight * 0.3f;
        float left = (viewWidth - frameSize) / 2f;
        float top = viewHeight / (parentSheetState==BottomSheetBehavior.STATE_EXPANDED?2.8f:5.5f);
        float right = left + frameSize;
        float bottom = top + frameSize;
        canvas.drawRect(left, top, right, bottom, scannerFramePaint);
    }

}
