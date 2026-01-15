package com.suisho.linktocomputer.activity;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;

import com.suisho.linktocomputer.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class CrashDialogActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
