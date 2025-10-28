package com.example.linktocomputer.activity;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import com.example.linktocomputer.R;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class CrashDialogActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.text_crash_dialog_title)
                .setMessage(R.string.text_crash_dialog_message)
                .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                    finish();
                    System.exit(1);
                    Process.killProcess(Process.myPid());
                })
                .setCancelable(false)
                .show();
    }
}
