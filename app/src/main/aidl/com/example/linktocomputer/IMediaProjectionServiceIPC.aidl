// IMediaProjectionServiceIPC.aidl
package com.example.linktocomputer;
import android.content.Intent;
// Declare any non-default types here with import statements

interface IMediaProjectionServiceIPC {
    void run();
    void setScreenIntent(in Intent data);
    void exit();
}