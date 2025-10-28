package com.example.linktocomputer.interfaces;

import com.google.zxing.Result;

public interface IQRCodeDetected {
    void onDetected(Result result);
}
