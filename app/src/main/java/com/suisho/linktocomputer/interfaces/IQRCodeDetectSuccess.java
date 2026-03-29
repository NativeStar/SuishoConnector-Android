package com.suisho.linktocomputer.interfaces;

import com.google.zxing.Result;

public interface IQRCodeDetectSuccess {
    void onDetected(Result result);
}
