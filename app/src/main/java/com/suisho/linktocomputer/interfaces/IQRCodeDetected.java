package com.suisho.linktocomputer.interfaces;

import com.google.zxing.Result;

public interface IQRCodeDetected {
    void onDetected(Result result);
}
