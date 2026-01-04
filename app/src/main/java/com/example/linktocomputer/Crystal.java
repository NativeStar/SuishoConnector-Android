package com.example.linktocomputer;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import com.example.linktocomputer.activity.CrashDialogActivity;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.database.MyObjectBox;
import com.example.linktocomputer.enums.ConnectionCloseCode;
import com.example.linktocomputer.service.MediaProjectionService;
import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import io.objectbox.BoxStore;

public class Crystal extends Application {
    private BoxStore database;
    @Override
    public void onCreate() {
        super.onCreate();
        applyDynamicColorsIfAvailable();
        initDatabase();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> new Thread(() -> {
            if(NewMainActivity.networkService!=null&&NewMainActivity.networkService.isConnected){
                NewMainActivity.networkService.disconnect(ConnectionCloseCode.CloseFromClientCrash,null);
            }
            //停止音频转发
            Intent audioProjectionServiceIntent=new Intent(this, MediaProjectionService.class);
            stopService(audioProjectionServiceIntent);
            File logDir = new File(getDataDir() + "/files/crash/");
            logDir.mkdirs();
            File logFile = new File(getDataDir() + "/files/crash/" + System.currentTimeMillis() + ".log");
            try (FileWriter fileWriter = new FileWriter(logFile)) {
                fileWriter.write("----------CRASH LOG HEADER BEGIN----------\n");
                fileWriter.write("Timestamp:" + System.currentTimeMillis() + "\n");
                fileWriter.write("OEM:" + Build.BRAND + "\n");
                fileWriter.write("Model:" + Build.MODEL + "\n");
                fileWriter.write("SDK version:" + Build.VERSION.SDK_INT + "\n");
                fileWriter.write("----------CRASH LOG HEADER END----------\n");
                PrintWriter printWriter = new PrintWriter(fileWriter);
                e.printStackTrace(printWriter);
                printWriter.close();
            } catch (IOException ex) {
                System.exit(1);
                Process.killProcess(Process.myPid());
            } finally {
                Intent intent = new Intent(getApplicationContext(), CrashDialogActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                System.exit(1);
                Process.killProcess(Process.myPid());
            }
        }).start());
    }
    private void initDatabase(){
        database=MyObjectBox.builder().androidContext(this).build();
    }
    public BoxStore getDatabase(){
        return database;
    }

    private void applyDynamicColorsIfAvailable() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            if(DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivitiesIfAvailable(this);
            }
        } catch (Throwable ignored) {
        }
    }
}
