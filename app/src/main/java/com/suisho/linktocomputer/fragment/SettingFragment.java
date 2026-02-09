package com.suisho.linktocomputer.fragment;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.suisho.linktocomputer.BuildConfig;
import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.R;
import com.suisho.linktocomputer.activity.NewMainActivity;
import com.suisho.linktocomputer.activity.StorageManageActivity;
import com.suisho.linktocomputer.constant.States;
import com.suisho.linktocomputer.instances.ComputerConfigManager;
import com.suisho.linktocomputer.instances.StateBarManager;
import com.suisho.linktocomputer.instances.adapter.TrustedDeviceListAdapter;
import com.suisho.linktocomputer.service.ConnectMainService;
import com.suisho.linktocomputer.service.NotificationListenerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SettingFragment extends PreferenceFragmentCompat {
    private ActivityResultLauncher<String> saveLogResultLauncher;
    private final Logger logger = LoggerFactory.getLogger(SettingFragment.class);

    public SettingFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //底部弹窗布局
        View trustDeviceManagerDialogLayout = getLayoutInflater().inflate(R.layout.bottom_trust_device_manager, null);
        View aboutDialogLayout = getLayoutInflater().inflate(R.layout.bottom_about_layout, null);
        aboutDialogLayout.findViewById(R.id.project_url_button).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/NativeStar/SuishoConnector-Android"));
            startActivity(intent);
            logger.debug("Open project github url");
        });
        //文本快捷发送 组件状态
        Activity activity = getActivity();
        ComponentName sendTextAliasComponent = new ComponentName(activity, activity.getPackageName() + ".TextUploadEntry");
        int componentState = activity.getPackageManager().getComponentEnabledSetting(sendTextAliasComponent);
        logger.debug("Text selection shortcut component state:{}", componentState);
        ((SwitchPreferenceCompat) findPreference("function_text_selection_shortcut")).setChecked(componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || componentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        //版本名称
        String finalDisplayName = String.format("%s(%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        ((TextView) aboutDialogLayout.findViewById(R.id.versionNameText)).setText(finalDisplayName);
        //设备列表view
        RecyclerView trustedDeviceRecyclerView = trustDeviceManagerDialogLayout.findViewById(R.id.trusted_device_list_recycler_view);
        //打开信任设备管理
        findPreference("key_open_trust_device_manager").setOnPreferenceClickListener((preference) -> {
            logger.debug("Open trust device manager sheet");
            ArrayList<TrustedDeviceListAdapter.DeviceTrustInstance> deviceTrustInstances = ComputerConfigManager.getAllComputers(getActivity());
            //无设备时提示
            if(deviceTrustInstances.isEmpty()) {
                logger.debug("No trusted device");
                Snackbar.make(getActivity().findViewById(R.id.coordinatorLayout3), R.string.trusted_device_manager_not_device, 2000).show();
                return true;
            }
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
            TrustedDeviceListAdapter trustedDeviceListAdapter = new TrustedDeviceListAdapter((NewMainActivity) getActivity(), deviceTrustInstances);
            trustedDeviceRecyclerView.setLayoutManager(layoutManager);
            trustedDeviceRecyclerView.setAdapter(trustedDeviceListAdapter);
            BottomSheetDialog trustDeviceManagerDialog = new BottomSheetDialog(getActivity());
            //无信任设备在列表时关闭弹窗
            trustedDeviceListAdapter.setOnDeviceListEmptyCallback(() -> {
                logger.debug("Cleaned all trusted device.Close sheet");
                if(trustDeviceManagerDialog.isShowing()) trustDeviceManagerDialog.dismiss();
            });
            trustDeviceManagerDialog.setContentView(trustDeviceManagerDialogLayout);
            trustDeviceManagerDialog.setCanceledOnTouchOutside(true);
            //移除控件 修复:The specified child already has a parent. You must call removeView() on the child's parent first
            trustDeviceManagerDialog.setOnDismissListener((pref) -> {
                ((ViewGroup) trustDeviceManagerDialogLayout.getParent()).removeAllViews();
            });
            trustDeviceManagerDialog.show();
            return true;
        });
        //打开存储空间管理activity
        findPreference("key_storage_manage").setOnPreferenceClickListener(pref -> {
            Intent intent = new Intent(getContext(), StorageManageActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            startActivity(intent);
            logger.debug("Open storage manage activity");
            return true;
        });
        //通知转发开关 状态改变监听
        findPreference("function_notification_forward").setOnPreferenceChangeListener((preference, newValue) -> {
            logger.info("Notification forward switch changed");
            boolean value = (boolean) newValue;
            if(NewMainActivity.networkService != null && NewMainActivity.networkService.getNotificationListenerService() != null) {
                NotificationListenerService notificationListenerService = NewMainActivity.networkService.getNotificationListenerService();
                notificationListenerService.setEnable(value);
            }
            NewMainActivity newMainActivity = (NewMainActivity) getActivity();
            //更改状态显示
            if(newMainActivity == null || newMainActivity.isDestroyed()) return true;
            StateBarManager stateBarManager = newMainActivity.stateBarManager;
            if(value) {
                if(stateBarManager != null && !newMainActivity.checkNotificationListenerPermission()) {
                    logger.debug("Not notification listener permission.Add state");
                    stateBarManager.addState(States.getStateList().get("info_notification_listener_permission"));
                }
            } else {
                if(stateBarManager != null) {
                    logger.debug("Has notification listener permission.Remove state");
                    stateBarManager.removeState("info_notification_listener_permission");
                }
            }
            return true;
        });
        findPreference("function_text_selection_shortcut").setOnPreferenceChangeListener((preference, newValue) -> {
            boolean value = (boolean) newValue;
            Activity activityNew = getActivity();
            ComponentName component = new ComponentName(activityNew, activityNew.getPackageName() + ".TextUploadEntry");
            activityNew.getPackageManager().setComponentEnabledSetting(component, value ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            logger.debug("Text selection shortcut switch change to {}", value);
            return true;
        });
        findPreference("file_save_location").setOnPreferenceClickListener(preference -> {
            logger.debug("Open file save location dialog");
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(R.string.dialog_transmit_file_save_location)
                    .setSingleChoiceItems(R.array.array_setting_dropdown_transmit_save_file_path, GlobalVariables.preferences.getInt("file_save_location", 0), (dialog, which) -> {
                        //需要用户打开权限并手动选择路径
                        //Download目录
                        if(which == 1) {
                            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                                logger.info("Change transmit file save location request storage permission because android version lower 10");
                                //读写存储空间权限
                                Context context = getContext();
                                if(context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    dialog.dismiss();
                                    new MaterialAlertDialogBuilder(getActivity())
                                            .setTitle(R.string.permission_request_alert_title)
                                            .setMessage(R.string.dialog_write_external_storage_permission_message)
                                            .setNegativeButton(R.string.text_cancel, (dialog1, which1) -> {
                                            })
                                            .setPositiveButton(R.string.text_ok, (dialog1, which1) -> {
                                                logger.debug("Request write external storage permission by change transmit file save location");
                                                getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                                            })
                                            .show();
                                    return;
                                }
                            }
                        }
                        logger.info("Change file save location to {}", which);
                        GlobalVariables.preferences.edit().putInt("file_save_location", which).apply();
                        Snackbar.make(getView(), "修改成功", 2000).show();
                        dialog.dismiss();
                    }).show();
            return true;
        });
        findPreference("key_export_transmit_files").setOnPreferenceClickListener(preference -> {
            //检查私有目录互传文件夹下是否有文件
            logger.debug("User request export in private directory transmit files");
            File transmitFilesPath = new File(getActivity().getExternalFilesDir(null).getAbsolutePath() + "/transmit/files/");
            if(!transmitFilesPath.exists() || transmitFilesPath.list().length == 0) {
                logger.debug("No transmit files in private directory.Don't export");
                Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), "私有目录中不存在互传文件 无需导出", 2500).show();
                return true;
            }
            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                logger.info("Export in private directory transmit files request storage permission because android version lower 10");
                //读写存储空间权限
                Context context = getContext();
                if(context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    new MaterialAlertDialogBuilder(getActivity())
                            .setTitle(R.string.permission_request_alert_title)
                            .setMessage(R.string.dialog_write_external_storage_permission_message)
                            .setNegativeButton(R.string.text_cancel, (dialog1, which1) -> {
                            })
                            .setPositiveButton(R.string.text_ok, (dialog1, which1) -> {
                                logger.debug("Request write external storage permission by Export in private directory transmit files");
                                getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                            })
                            .show();
                    return true;
                }
            }
            String path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector").getAbsolutePath();
            logger.debug("Show export transmit file confirm dialog.Target path:{}", path);
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle("导出互传文件")
                    .setMessage("会将所有私有目录下的互传文件导出至'" + path + "'目录下并删除源文件\n是否确定?")
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("确定", (dialog, which) -> {
                        dialog.dismiss();
                        Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), "开始导出 请等待完成提示", 2500).show();
                        logger.info("Start export transmit files");
                        new Thread(() -> {
                            if(moveTransmitFiles(transmitFilesPath)) {
                                getActivity().runOnUiThread(() -> {
                                    Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), "导出完成", 2500).show();
                                });
                            } else {
                                getActivity().runOnUiThread(() -> {
                                    Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), "导出失败", 2500).show();
                                });
                            }
                        }).start();
                    }).show();
            return true;
        });
        findPreference("function_file_manager").setOnPreferenceChangeListener((preference, newValue) -> {
            logger.debug("User request change remote file manager function");
            if(((boolean) newValue)) {
                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    if(!Environment.isExternalStorageManager()) {
                        logger.info("Not storage permission on android 10 or newer.Request permission");
                        showStoragePermissionDialog();
                        return false;
                    }
                } else {
                    Activity checkPermissionActivity = getActivity();
                    if(checkPermissionActivity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED || checkPermissionActivity.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                        logger.info("Not storage permission on android 9 or lower.Request permission");
                        showStoragePermissionDialog();
                        return false;
                    }
                }
            }
            ConnectMainService service = NewMainActivity.networkService;
            if(service == null || !service.isConnected) return true;
            try {
                if((boolean) newValue) {
                    service.webFileServer.start();
                } else {
                    service.webFileServer.stop();
                }
            } catch (IOException ioe) {
                logger.error("Error when start/stop file server", ioe);
                Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.error_change_file_manager, 3000).show();
                findPreference("function_file_manager").setEnabled(false);
            }
            logger.info("Changed remote file manager function state");
            return true;
        });
        findPreference("key_clear_auto_connect_file").setOnPreferenceClickListener((preference -> {
            logger.debug("User request clear auto connect file");
            File keyFile = new File(getActivity().getFilesDir() + "/bind.key");
            if(!keyFile.exists()) {
                logger.debug("Bind device file not found");
                Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_unbind_failed_not_bound, 2000).show();
                return true;
            }
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(R.string.dialog_clear_auto_connect_file_title)
                    .setMessage(R.string.dialog_clear_auto_connect_file_desc)
                    .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                        //解绑计算机
                        keyFile.delete();
                        GlobalVariables.settings.edit().putBoolean("boundDevice", false).apply();
                        logger.info("Unbind computer");
                        Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_unbind_success, 2000).show();
                    })
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        }));
        findPreference("key_export_crash_logs").setOnPreferenceClickListener((preference -> {
            logger.debug("User request export crash logs");
            saveLogResultLauncher.launch("logs-" + System.currentTimeMillis() + ".zip");
            return true;
        }));
        findPreference("key_about").setOnPreferenceClickListener((v) -> {
            logger.debug("Show about sheet");
            BottomSheetDialog aboutSheetDialog = new BottomSheetDialog(getActivity());
            aboutSheetDialog.setContentView(aboutDialogLayout);
            aboutSheetDialog.setCanceledOnTouchOutside(true);
            aboutSheetDialog.setOnDismissListener(dialog -> ((ViewGroup) aboutDialogLayout.getParent()).removeAllViews());
            aboutSheetDialog.show();
            return true;
        });
        try {
            initLaunchVerifySwitch();
        } catch (Exception e) {
            logger.error("Failed to init launch verify switch", e);
            findPreference("function_launch_verify").setSummary(R.string.setting_launch_verify_summary_exception);
        }
        saveLogResultLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), this::exportAllLogs);
    }

    private void initLaunchVerifySwitch() {
        BiometricManager biometricManager = BiometricManager.from(getActivity());
        int canAuth;
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            logger.debug("Init biometric manager on android 9 and lower");
            canAuth = biometricManager.canAuthenticate(DEVICE_CREDENTIAL | BIOMETRIC_WEAK);
        } else {
            logger.debug("Init biometric manager with android 10 and newer");
            canAuth = biometricManager.canAuthenticate(DEVICE_CREDENTIAL | BIOMETRIC_STRONG);
        }
        switch (canAuth) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                findPreference("function_launch_verify").setEnabled(true);
                logger.debug("Device supported biometric");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                findPreference("function_launch_verify").setSummary(R.string.setting_launch_verify_summary_device_not_password);
                logger.debug("Device supported biometric but not set password");
                break;
            default:
                findPreference("function_launch_verify").setSummary(R.string.setting_launch_verify_summary_unsupported);
                logger.debug("Device not supported biometric");
                break;
        }
        findPreference("function_launch_verify").setOnPreferenceChangeListener((preference, newValue) -> {
            logger.info("User request change launch verify state.Start change verify");
            BiometricPrompt prompt = new BiometricPrompt.Builder(getActivity())
                    .setTitle(getString(R.string.auth_dialog_title))
                    .setSubtitle(getString(R.string.auth_dialog_desc))
                    .setDeviceCredentialAllowed(true)
                    .build();
            prompt.authenticate(new CancellationSignal(), getActivity().getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    ((SwitchPreferenceCompat) preference).setChecked((Boolean) newValue);
                    Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_verify_success, 2500).show();
                    logger.info("Verify success.Change launch verify function state");
                }
            });
            return false;
        });
    }

    private void showStoragePermissionDialog() {
        logger.debug("Show storage permission dialog");
        new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.permission_request_alert_title)
                .setMessage(R.string.setting_storage_permission_dialog_message)
                .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                    if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        logger.debug("Start access all file permission activity for android 10 or newer");
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                        getActivity().startActivity(intent);
                    } else {
                        logger.debug("Request storage permission for android 9 and lower");
                        getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                    }
                })
                .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                })
                .show();

    }

    private boolean moveTransmitFiles(File transmitFilesPath) {
        logger.info("Start move transmit files");
        File[] transmitFiles = transmitFilesPath.listFiles();
        //防止文件名冲突
        File targetDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit/");
        File[] targetPathFiles = targetDirectory.listFiles();
        if(transmitFiles == null) {
            logger.warn("Failed to move transmit files.Path is null");
            return false;
        }
        for(File file : transmitFiles) {
            Path targetPath = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit/" + file.getName()).toPath();
            logger.debug("Move file {} to {}", file.getAbsolutePath(), targetPath);
            try {
                //判断重名
                for(File targetDirectoryFile : targetPathFiles) {
                    if(targetDirectoryFile.getName().equals(file.getName())) {
                        //文件名追加时间戳
                        targetPath = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit/" + System.currentTimeMillis() + file.getName()).toPath();
                        logger.debug("File name conflict.Append timestamp to file name.Now path and name:{}", targetPath);
                        break;
                    }
                }
                Files.copy(file.toPath(), targetPath);
                file.delete();
                logger.debug("Move file success");
            } catch (IOException e) {
                logger.error("Move file failed with exception", e);
                return false;
            }
        }
        logger.info("Move transmit files success");
        return true;
    }

    private void exportAllLogs(Uri uri) {
        if(uri == null) return;
        //TODO 改SAF导出
        logger.info("Start export all logs");
        File crashLogDirectory = new File(getActivity().getDataDir() + "/files/crash/");
        File commonLogDirectory = new File(getActivity().getDataDir() + "/files/logs/");
        crashLogDirectory.mkdirs();
        commonLogDirectory.mkdirs();
        if(!crashLogDirectory.isDirectory() && !commonLogDirectory.isDirectory()) {
            logger.debug("No log file folder found");
            Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_not_log, 2500).show();
            return;
        }
        File[] crashLogFiles = crashLogDirectory.listFiles();
        File[] commonLogFiles = commonLogDirectory.listFiles();
        if(commonLogFiles == null || crashLogFiles == null) {
            logger.warn("Open logs folder failed!");
            Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_not_log, 2500).show();
            return;
        }
        if(crashLogFiles.length == 0 && commonLogFiles.length == 0) {
            logger.debug("No log file found");
            Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_not_log, 2500).show();
            return;
        }
        new Thread(() -> {
            try {
                OutputStream rawStream = getContext().getContentResolver().openOutputStream(uri);
                ZipOutputStream zipOutputStream = new ZipOutputStream(rawStream);
                //崩溃日志
                for(File crashLogFile : crashLogFiles) {
                    FileInputStream inputStream = new FileInputStream(crashLogFile);
                    zipOutputStream.putNextEntry(new ZipEntry("crash/" + crashLogFile.getName()));
                    byte[] buffer = new byte[(int) crashLogFile.length()];
                    inputStream.read(buffer);
                    zipOutputStream.write(buffer);
                    inputStream.close();
                    zipOutputStream.closeEntry();
                }
                //运行日志
                for(File commonLogFile : commonLogFiles) {
                    FileInputStream inputStream = new FileInputStream(commonLogFile);
                    zipOutputStream.putNextEntry(new ZipEntry("common/" + commonLogFile.getName()));
                    byte[] buffer = new byte[(int) commonLogFile.length()];
                    inputStream.read(buffer);
                    zipOutputStream.write(buffer);
                    inputStream.close();
                    zipOutputStream.closeEntry();
                }
                zipOutputStream.flush();
                zipOutputStream.close();
                rawStream.close();
                getActivity().runOnUiThread(() -> Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), getString(R.string.text_export_success), 3500).show());
                logger.info("Export all logs success");
            } catch (IOException e) {
                logger.error("Export all logs failed with exception", e);
                getActivity().runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(getActivity())
                            .setTitle(R.string.text_export_failed)
                            .setMessage(e.getMessage())
                            .setPositiveButton(R.string.text_ok, (dialog, which) -> dialog.dismiss())
                            .setCancelable(false)
                            .show();
                });
            }
        }).start();
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.setting_fragment_list);
    }
}