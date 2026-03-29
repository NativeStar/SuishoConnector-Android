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
import com.suisho.linktocomputer.enums.CodeScannerState;

public class CodeScannerOverlay extends View {
    private CodeScannerState state = CodeScannerState.DEFAULT;
    private final Paint scannerFramePaint = new Paint();
    private final Paint scannerTextPaint = new Paint();
    private int parentSheetState = BottomSheetBehavior.STATE_COLLAPSED;

    public CodeScannerOverlay(Context context) {
        super(context);
        init();
    }

    public CodeScannerOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        scannerFramePaint.setColor(Color.RED);
        scannerFramePaint.setStrokeWidth(5);
        scannerFramePaint.setStyle(Paint.Style.STROKE);
        scannerTextPaint.setColor(Color.WHITE);
        scannerTextPaint.setTextSize(65);
    }

    public void setState(CodeScannerState newState) {
        //避免重复触发更新
        if(newState != state) {
            this.state = newState;
            post(()->{
                if(newState==CodeScannerState.DEFAULT) {
                    scannerTextPaint.setColor(Color.WHITE);
                }else{
                    scannerTextPaint.setColor(Color.RED);
                }
                invalidate();
            });
        }
    }

    public void setParentSheetDialog(BottomSheetDialog parentSheetDialog) {
        parentSheetDialog.getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if(newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_EXPANDED) {
                    parentSheetState = newState;
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
        float top = viewHeight / (parentSheetState == BottomSheetBehavior.STATE_EXPANDED ? 2.8f : 5.5f);
        float right = left + frameSize;
        float bottom = top + frameSize;
        canvas.drawRect(left, top, right, bottom, scannerFramePaint);
        //暂时就这点状态 懒得写动态计算了 应付一下吧
        canvas.drawText(state == CodeScannerState.DEFAULT ? "扫描PC端连接二维码" : "无效二维码", (float) (viewWidth * (state == CodeScannerState.DEFAULT ? 0.3f : 0.4f)), bottom + 80, scannerTextPaint);
    }
}