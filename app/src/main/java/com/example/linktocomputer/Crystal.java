package com.example.linktocomputer;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;

import com.example.linktocomputer.activity.CrashDialogActivity;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.database.MyObjectBox;
import com.example.linktocomputer.enums.ConnectionCloseCode;
import com.google.android.material.color.DynamicColors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import io.objectbox.BoxStore;

public class Crystal extends Application {
    private BoxStore database;
    private Logger logger;

    @Override
    public void onCreate() {
        super.onCreate();
        initLogger();
        applyDynamicColorsIfAvailable();
        initDatabase();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> new Thread(() -> {
            logger.error("Fatal error!!!", e);
            if(NewMainActivity.networkService != null && NewMainActivity.networkService.isConnected) {
                NewMainActivity.networkService.disconnect(ConnectionCloseCode.CloseFromClientCrash, null);
                //停止音频转发
                if(NewMainActivity.networkService.projectionServiceIPC != null) {
                    try {
                        NewMainActivity.networkService.projectionServiceIPC.exit();
                    } catch (RemoteException ignore) {
                    }
                }
            }
            File logDir = new File(getDataDir() + "/files/crash/");
            logDir.mkdirs();
            File logFile = new File(getDataDir() + "/files/crash/" + System.currentTimeMillis() + ".log");
            try (FileWriter fileWriter = new FileWriter(logFile)) {
                fileWriter.write("----------CRASH LOG HEADER BEGIN----------\n");
                fileWriter.write("Timestamp:" + System.currentTimeMillis() + "\n");
                fileWriter.write("OEM:" + Build.BRAND + "\n");
                fileWriter.write("Model:" + Build.MODEL + "\n");
                fileWriter.write("SDK version:" + Build.VERSION.SDK_INT + "\n");
                fileWriter.write("App version:" + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + "\n");
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

    private void initDatabase() {
        database = MyObjectBox.builder().androidContext(this).build();
    }

    public BoxStore getDatabase() {
        return database;
    }

    private void applyDynamicColorsIfAvailable() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            if(DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivitiesIfAvailable(this);
                logger.debug("Dynamic colors applied");
                return;
            }
            logger.debug("Dynamic colors unavailable");
        } catch (Throwable error) {
            logger.error("Dynamic colors apply crash", error);
        }
    }

    private void initLogger() {
        GlobalVariables.preferences = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger settingLevelLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        boolean enableDebugLog = GlobalVariables.preferences.getBoolean("enable_debug_log", false);
        settingLevelLogger.setLevel(enableDebugLog ? Level.DEBUG : Level.INFO);
        Appender<ILoggingEvent> appender = settingLevelLogger.getAppender("file");
        if(appender instanceof FileAppender) {
            GlobalVariables.currentBootLogFilePath =((FileAppender<?>) appender).getFile();
            //拿不到就算 反正文件删了也不会崩
        }
        logger = LoggerFactory.getLogger(Crystal.class);
        logger.info("Application init");
        String infoStr = "\nOEM:" + Build.BRAND + "\n" +
                "Model:" + Build.MODEL + "\n" +
                "SDK version:" + Build.VERSION.SDK_INT + "\n" +
                "App version:" + BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE + "\n";
        logger.info(infoStr);
    }
}
