// IMediaProjectionServiceIPC.aidl
package com.suisho.linktocomputer;
import android.content.Intent;
// Declare any non-default types here with import statements

interface IMediaProjectionServiceIPC {
    void run();
    void setTargetAddress(String targetAddress);
    void setScreenIntent(in Intent data);
    void setEncryptData(String keyBase64, String ivBase64);
    void exit();
}