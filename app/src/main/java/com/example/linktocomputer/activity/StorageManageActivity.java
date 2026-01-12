package com.example.linktocomputer.activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.appcompat.app.AppCompatActivity;

import com.example.linktocomputer.Crystal;
import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.database.TransmitDatabaseEntity;
import com.example.linktocomputer.service.ConnectMainService;
import com.example.linktocomputer.view.ConfirmSeekBar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.objectbox.Box;

public class StorageManageActivity extends AppCompatActivity {
    private StorageStatsManager storageStatsManager;
    private List<StorageVolume> storageVolumes;
    //退出时终止进程标志
    private boolean requestSuicide = false;
    private final Logger logger = LoggerFactory.getLogger(StorageManageActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_manage);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        //只能再onCreate或其之后初始化
        storageStatsManager = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
        StorageManager storageManager = (StorageManager) getSystemService(STORAGE_SERVICE);
        storageVolumes = storageManager.getStorageVolumes();
        init();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if(requestSuicide) {
            logger.debug("User pressed back and application request suicide");
            moveTaskToBack(true);
            finishAffinity();
            System.exit(0);
        } else {
            logger.debug("User pressed back.Only common finish activity");
            finish();
        }
    }

    private void init() {
        //Android13适配
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if(requestSuicide) {
                    logger.debug("User pressed Invoke(Tiramisu) and application request suicide");
                    moveTaskToBack(true);
                    finishAffinity();
                    System.exit(0);
                } else {
                    logger.debug("User pressed Invoke(Tiramisu).Only common finish activity");
                    finish();
                }
            });
        }
        new Thread(() -> {
            //文本显示
            if(initTextShow()) return;
            //按钮功能
            //清理缓存
            findViewById(R.id.button_storage_manage_clear_cache).setOnClickListener((view) -> {
                if(isConnected()) {
                    closeConnectionDialog();
                    return;
                }
                logger.info("User request clear cache");
                File cacheDir = new File(getCacheDir() + "/");
                if(cacheDir.exists()) {
                    if(!clearFolder(cacheDir)) {
                        exceptionOnInitDialog("Error on clear cache");
                        return;
                    }
                }
                Snackbar.make(findViewById(R.id.storage_manage_activity_root), getString(R.string.text_cleared), 2000).show();
                finishActivity();
                initTextShow();
            });
            //互传清理
            findViewById(R.id.button_storage_manage_clear_transmit_data).setOnClickListener((view) -> {
                //不终止的的话 互传记录可能会在过后再次被写入
                if(isConnected()) {
                    closeConnectionDialog();
                    return;
                }
                logger.debug("Open transmit data clear confirm");
                new MaterialAlertDialogBuilder(this)
                        .setTitle("清理确认")
                        .setMessage("将清空互传接收的文件和传输记录\n确认继续?")
                        .setNegativeButton("取消", (dialog, which) -> {
                        })
                        .setPositiveButton("确认", (dialog, which) -> {
                            logger.info("User request clear transmit data");
                            //再次终止Activity
                            finishActivity();
                            //接收文件文件夹
                            File transmitDataPath = new File(getExternalFilesDir(null).getAbsolutePath() + "/transmit/files/");
                            Box<TransmitDatabaseEntity> database = ((Crystal) getApplication()).getDatabase().boxFor(TransmitDatabaseEntity.class);
                            database.removeAll();
                            clearFolder(transmitDataPath);
                            Snackbar.make(findViewById(R.id.storage_manage_activity_root), getString(R.string.text_cleared), 2000).show();
                            //确保显示内容刷新 否则可能误以为清理失败
                            requestSuicide = true;
                            finishActivity();
                            initTextShow();
                        }).show();
            });
            findViewById(R.id.button_storage_manage_clear_chaos_data).setOnClickListener(v -> {
                if(isConnected()) {
                    closeConnectionDialog();
                    return;
                }
                logger.debug("Open chaos data clear confirm");
                new MaterialAlertDialogBuilder(this)
                        .setTitle("清理确认")
                        .setMessage("确认清除杂项数据?\n(不会清理本次运行产生的日志文件)")
                        .setNegativeButton("取消", (dialog, which) -> {
                        })
                        .setPositiveButton("确认", (dialog, which) -> {
                            logger.info("User request clear chaos data");
                            //加密连接用证书
                            File certFolder = new File(getDataDir().getAbsolutePath() + "/files/cert/");
                            clearFolder(certFolder);
                            //崩溃日志
                            File crashLogsFolder = new File(getDataDir().getAbsolutePath() + "/files/crash/");
                            clearFolder(crashLogsFolder);
                            //运行日志
                            File commonLogsFolder = new File(getDataDir().getAbsolutePath() + "/files/logs/");
                            clearFolder(commonLogsFolder,true);
                            Snackbar.make(findViewById(R.id.storage_manage_activity_root), getString(R.string.text_cleared), 2000).show();
                            initTextShow();
                        }).show();
            });
            findViewById(R.id.button_storage_manage_wipe_data).setOnClickListener(v -> {
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
                View wipeDataLayout = getLayoutInflater().inflate(R.layout.bottom_wipe_data_confirm, null, false);
                ConfirmSeekBar wipeDataSeekBar = wipeDataLayout.findViewById(R.id.wipe_data_confirm_seek_bar);
                wipeDataSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if(progress == 100 && fromUser) {
                            logger.info("User request wipe data,all logs will delete.Goodbye World");
                            bottomSheetDialog.cancel();
                            new MaterialAlertDialogBuilder(StorageManageActivity.this)
                                    .setTitle(getString(R.string.dialog_wipe_data_title))
                                    .setMessage(getString(R.string.dialog_wipe_data_message))
                                    .setCancelable(false)
                                    .show();
                            new Thread(() -> {
                                ActivityManager activityManager = StorageManageActivity.this.getSystemService(ActivityManager.class);
                                try {
                                    //大概让人看清字
                                    Thread.sleep(750);
                                } catch (InterruptedException err) {
                                    logger.error("Error when thread sleep,But nothing happened.Call wiping data", err);
                                } finally {
                                    activityManager.clearApplicationUserData();
                                }
                            }).start();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                bottomSheetDialog.setContentView(wipeDataLayout);
                bottomSheetDialog.setCanceledOnTouchOutside(true);
                logger.debug("Open all data clear confirm sheet");
                bottomSheetDialog.show();
            });
        }).start();
    }

    private boolean initTextShow() {
        logger.debug("Calc storage usage...");
        StorageStats selfStorageState = null;
        for(StorageVolume volume : storageVolumes) {
            UUID volumeUuid;
            String uuidString = volume.getUuid();
            if(uuidString == null || uuidString.isEmpty()) {
                volumeUuid = StorageManager.UUID_DEFAULT;
            } else {
                volumeUuid = UUID.fromString(uuidString);
            }
            try {
                selfStorageState = storageStatsManager.queryStatsForPackage(volumeUuid, getPackageName(), Process.myUserHandle());
                //如果拿不到数据上面就崩了
                break;
            } catch (IOException e) {
                logger.error("Error when query storage state", e);
                exceptionOnInitDialog(e.getMessage());
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                //正常应该不会吧
                logger.error("What the fuck???Cannot query self package name???", e);
                exceptionOnInitDialog(e.getMessage());
                return true;
            }
        }
        //虽然感觉没什么必要
        if(selfStorageState == null) {
            exceptionOnInitDialog("Error on get storage state");
            return true;
        }
        //总占用
        ((TextView) findViewById(R.id.text_storage_usage_app)).setText(Util.coverFileSize(selfStorageState.getAppBytes() + selfStorageState.getCacheBytes() + selfStorageState.getDataBytes()));
        //缓存
        ((TextView) findViewById(R.id.text_storage_usage_cache)).setText(Util.coverFileSize(selfStorageState.getCacheBytes()));
        //互传记录及文件
        File transmitDataPath = new File(getExternalFilesDir(null).getAbsolutePath() + "/transmit/files/");
        //传输记录文件
        long transmitFilesTotalSize = 0L;
        File transmitRecordFile = new File(getDataDir().getAbsolutePath() + "/files/objectbox/objectbox/data.mdb");
        if(transmitRecordFile.exists()) {
            transmitFilesTotalSize += transmitRecordFile.length();
        }
        transmitFilesTotalSize += getFolderSize(transmitDataPath);
        //互传占用 文本
        ((TextView) findViewById(R.id.text_storage_usage_transmit)).setText(Util.coverFileSize(transmitFilesTotalSize));
        //杂项占用
        long chaosFilesTotalSize = 0L;
        //证书
        File certFolder = new File(getDataDir().getAbsolutePath() + "/files/cert/");
        chaosFilesTotalSize += getFolderSize(certFolder);
        //崩溃日志
        File crashLogsFolder = new File(getDataDir().getAbsolutePath() + "/files/crash/");
        chaosFilesTotalSize += getFolderSize(crashLogsFolder);
        //运行日志
        File commonLogsFolder = new File(getDataDir().getAbsolutePath() + "/files/logs/");
        chaosFilesTotalSize += getFolderSize(commonLogsFolder);
        ((TextView) findViewById(R.id.text_storage_usage_chaos_data)).setText(Util.coverFileSize(chaosFilesTotalSize));
        //设置按钮可用
        runOnUiThread(() -> {
            findViewById(R.id.button_storage_manage_clear_cache).setEnabled(true);
            findViewById(R.id.button_storage_manage_clear_transmit_data).setEnabled(true);
            findViewById(R.id.button_storage_manage_clear_chaos_data).setEnabled(true);
            findViewById(R.id.button_storage_manage_wipe_data).setEnabled(true);
        });
        logger.debug("Storage usage calc done");
        return false;
    }

    private void exceptionOnInitDialog(String message) {
        logger.warn("Show error dialog with message:{}", message);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("确定", ((dialog, which) -> this.finish()))
                .show();
    }

    /**
     * 计算文件夹占用大小 不算子文件夹的
     *
     * @param path 路径File对象
     */
    private long getFolderSize(File path) {
        long tempFilesSize = 0L;
        if(path.exists() && path.isDirectory()) {
            File[] filesList = path.listFiles();
            if(filesList == null) return tempFilesSize;
            for(File file : filesList) {
                tempFilesSize += file.length();
            }
        }
        logger.debug("Calc folder '{}' size {}",path.getName(), tempFilesSize);
        return tempFilesSize;
    }
    private boolean clearFolder(File path) {
        return clearFolder(path,false);
    }
    /**
     * 清空文件夹 仅限其中文件
     *
     * @param path 目标文件内File
     * @return 是否完成清理
     */
    private boolean clearFolder(File path,boolean isLog) {
        if(path.exists()) {
            File[] files = path.listFiles();
            //文件夹不存在
            if(files == null) return true;
            for(File file : files) {
                if(isLog&&file.getPath().equals(GlobalVariables.currentBootLogFilePath))continue;
                if(!file.delete()) return false;
            }
            return true;
        }
        return true;
    }

    private boolean isConnected() {
        return (GlobalVariables.computerConfigManager != null && GlobalVariables.computerConfigManager.getNetworkService().isConnected);
    }

    private void closeConnectionDialog() {
        logger.debug("Show close connection dialog");
        new MaterialAlertDialogBuilder(this)
                .setTitle("关闭连接")
                .setMessage("执行该清理项前需要关闭连接且清理后软件将关闭\n确认继续?")
                .setNegativeButton("取消", (dialog, which) -> {
                })
                .setPositiveButton("确定", (dialog, which) -> {
                    GlobalVariables.computerConfigManager.getNetworkService().disconnect();
                    finishActivity();
                }).show();
    }

    /**
     * 结束主activity
     */
    private void finishActivity() {
        logger.debug("Finishing main activity");
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        activityManager.getAppTasks().forEach(appTask -> {
            if(appTask.getTaskInfo().baseIntent.getComponent().getShortClassName().equals(NewMainActivity.class.getName())) {
                appTask.finishAndRemoveTask();
            }
        });
        if(GlobalVariables.computerConfigManager == null) return;
        ConnectMainService networkService = GlobalVariables.computerConfigManager.getNetworkService();
        if(networkService == null) return;
        Activity activity = networkService.activityMethods.getActivity();
        if(activity != null && !(activity.isDestroyed() || activity.isFinishing())) {
            activity.finish();
            activity.finishAndRemoveTask();
        }
    }
}
