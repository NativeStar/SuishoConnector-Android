package com.example.linktocomputer.fragment;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.activity.StorageManageActivity;
import com.example.linktocomputer.constant.States;
import com.example.linktocomputer.instances.ComputerConfigManager;
import com.example.linktocomputer.instances.StateBarManager;
import com.example.linktocomputer.instances.adapter.TrustedDeviceListAdapter;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SettingFragment extends PreferenceFragmentCompat {
    private ActivityResultLauncher<Intent> pickDirectoryCallback;

    public SettingFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //底部弹窗布局
        View trustDeviceManagerDialogLayout = getLayoutInflater().inflate(R.layout.bottom_trust_device_manager, null);
        //设备列表view
        RecyclerView trustedDeviceRecyclerView = trustDeviceManagerDialogLayout.findViewById(R.id.trusted_device_list_recycler_view);
        pickDirectoryCallback = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback() {
            @Override
            public void onActivityResult(Object result) {
                if(result == null) return;
                ActivityResult activityResult = (ActivityResult) result;
                Intent resultIntent = activityResult.getData();
                if(resultIntent == null) return;
                Log.i("main", resultIntent.toString());
                //去除旧的权限
                ContentResolver resolver = getContext().getContentResolver();
                UriPermission uriPermission = resolver.getPersistedUriPermissions().get(0);
                if(uriPermission != null) {
                    resolver.releasePersistableUriPermission(uriPermission.getUri(), (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                }
                resolver.takePersistableUriPermission(resultIntent.getData(), (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            }
        });
        //打开信任设备管理
        findPreference("key_open_trust_device_manager").setOnPreferenceClickListener((preference) -> {
            ArrayList<TrustedDeviceListAdapter.DeviceTrustInstance> deviceTrustInstances = ComputerConfigManager.getAllComputers(getActivity());
            //无设备时提示
            if(deviceTrustInstances.isEmpty()) {
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
            return true;
        });
        //通知转发开关 状态改变监听
        findPreference("function_notification_forward").setOnPreferenceChangeListener((preference, newValue) -> {
            boolean value = (boolean) newValue;
            ComputerConfigManager configManager = GlobalVariables.computerConfigManager;
            NewMainActivity activity = (NewMainActivity) getActivity();
            if(configManager != null) {
                ConnectMainService networkService = configManager.getNetworkService();
                //调整处理状态
                networkService.getNotificationListenerService().setEnable(value);
                //告知pc端
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("packetType", "render_client_function_state_change");
                jsonObject.addProperty("type", "notificationForwardEnable");
                jsonObject.addProperty("value", value);
                networkService.sendObject(jsonObject);
            }
            //更改状态显示
            if(activity == null) return true;
            StateBarManager stateBarManager = activity.stateBarManager;
            if(value) {
                if(stateBarManager != null && !activity.checkNotificationListenerPermission()) {
                    stateBarManager.addState(States.getStateList().get("info_notification_listener_permission"));
                }
            } else {
                if(stateBarManager != null) {
                    stateBarManager.removeState("info_notification_listener_permission");
                }
            }
            return true;
        });
        findPreference("file_save_location").setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(R.string.dialog_transmit_file_save_location)
                    .setSingleChoiceItems(R.array.array_setting_dropdown_transmit_save_file_path, GlobalVariables.preferences.getInt("file_save_location", 0), (dialog, which) -> {
                        //需要用户打开权限并手动选择路径
                        //Download目录
                        if(which == 1) {
                            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
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
                                                getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                                            })
                                            .show();
                                    return;
                                }
                            }
                        }
                        GlobalVariables.preferences.edit().putInt("file_save_location", which).apply();
                        Snackbar.make(getView(), "修改成功", 2000).show();
//                        if(which == 2) {
//                            //检测权限
//                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                                //所有文件访问权限
//                                if(!Environment.isExternalStorageManager()) {
//                                    dialog.dismiss();
//                                    new MaterialAlertDialogBuilder(getActivity())
//                                            .setTitle(R.string.permission_request_alert_title)
//                                            .setMessage(R.string.dialog_manage_all_files_permission_message)
//                                            .setNegativeButton(R.string.text_cancel, (dialog1, which1) -> {
//                                                dialog1.cancel();
//                                            })
//                                            .setCancelable(false)
//                                            .setPositiveButton(R.string.text_ok, (dialog1, which1) -> {
//                                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
//                                                startActivity(intent);
//                                            })
//                                            .show();
//                                    return;
//                                }
//                            } else {
//                                //读写存储空间权限
//                                Context context = getContext();
//                                if(context.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED || context.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
//                                    dialog.dismiss();
//                                    new MaterialAlertDialogBuilder(getActivity())
//                                            .setTitle(R.string.permission_request_alert_title)
//                                            .setMessage(R.string.dialog_write_external_storage_permission_message)
//                                            .setNegativeButton(R.string.text_cancel, (dialog1, which1) -> {
//                                            })
//                                            .setPositiveButton(R.string.text_ok, (dialog1, which1) -> {
//                                                getActivity().requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, 1000);
//                                            })
//                                            .show();
//                                    return;
//                                }
//                            }
//                            //要求设定目录
//                            Intent openDocumentTreeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//                            //持久化授权
//                            openDocumentTreeIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
//                            pickDirectoryCallback.launch(openDocumentTreeIntent);
////                            getContext().getContentResolver().getPersistedUriPermissions()
//                        } else {
////                            Snackbar.make()
//                        }
                        dialog.dismiss();
                    }).show();
            return true;
        });
        findPreference("key_export_transmit_files").setOnPreferenceClickListener(preference -> {
            //检查私有目录互传文件夹下是否有文件
            File transmitFilesPath = new File(getActivity().getExternalFilesDir(null).getAbsolutePath() + "/transmit/files/");
            if(!transmitFilesPath.exists() || transmitFilesPath.list().length == 0) {
                Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), "私有目录中不存在互传文件 无需导出", 2500).show();
                return true;
            }
            String path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector").getAbsolutePath();
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle("导出互传文件")
                    .setMessage("会将所有私有目录下的互传文件导出至'" + path + "'目录下并删除源文件\n是否确定?")
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                    .setPositiveButton("确定", (dialog, which) -> {
                        dialog.dismiss();
                        Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), "开始导出", 2500).show();
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
//            File[] transmitFiles=transmitFilesPath.listFiles();
//            if(transmitFiles == null) {
//                return true;
//            }
//            for(File file:transmitFiles){
//                Files.copy()
//            }
            return true;
        });
        findPreference("function_file_manager").setOnPreferenceChangeListener((preference, newValue) -> {
            if(((boolean) newValue)) {
                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    if(!Environment.isExternalStorageManager()) {
                        showStoragePermissionDialog();
                        return false;
                    }
                } else {
                    Activity activity = getActivity();
                    if(activity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                        showStoragePermissionDialog();
                        return false;
                    }
                }
            }
            Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_active_after_reboot, 3000).show();
            findPreference("function_file_manager").setEnabled(false);
            return true;
        });
        findPreference("key_clear_auto_connect_file").setOnPreferenceClickListener((preference -> {
            File keyFile = new File(getActivity().getFilesDir() + "/bind.key");
            if(!keyFile.exists()) {
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
                        Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.text_unbind_success, 2000).show();
                    })
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        }));
        findPreference("key_export_crash_logs").setOnPreferenceClickListener((preference -> {
            new MaterialAlertDialogBuilder(getActivity())
                    .setMessage(R.string.dialog_log_export_message)
                    .setPositiveButton(R.string.text_ok, ((dialog, which) -> exportCrashReport()))
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> dialog.dismiss())
                    .setCancelable(false)
                    .show();
            return true;
        }));
        try {
            initLaunchVerifySwitch();
        } catch (Exception e) {
            findPreference("function_launch_verify").setSummary(R.string.setting_launch_verify_summary_exception);
        }
    }

    private void initLaunchVerifySwitch() {
        BiometricManager biometricManager = BiometricManager.from(getActivity());
        int canAuth;
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            canAuth = biometricManager.canAuthenticate(DEVICE_CREDENTIAL | BIOMETRIC_WEAK);
        } else {
            canAuth = biometricManager.canAuthenticate(DEVICE_CREDENTIAL | BIOMETRIC_STRONG);
        }
        switch (canAuth) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                findPreference("function_launch_verify").setEnabled(true);
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                findPreference("function_launch_verify").setSummary(R.string.setting_launch_verify_summary_device_not_password);
                break;
            default:
                findPreference("function_launch_verify").setSummary(R.string.setting_launch_verify_summary_unsupported);
                break;
        }
        findPreference("function_launch_verify").setOnPreferenceChangeListener((preference, newValue) -> {
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
                }
            });
            return false;
        });
    }

    private void showStoragePermissionDialog() {
        new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.permission_request_alert_title)
                .setMessage(R.string.setting_storage_permission_dialog_message)
                .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                    if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                        getActivity().startActivity(intent);
                    } else {
                        getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                    }
//                        Activity activity=getActivity();
//                        if(activity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED ||activity.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
//                            showStoragePermissionDialog();
//                            return false;
//                        }
//                    }
                })
                .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                })
                .show();

    }

    private boolean moveTransmitFiles(File transmitFilesPath) {
        File[] transmitFiles = transmitFilesPath.listFiles();
        //防止文件名冲突
        File targetDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit/");
        File[] targetPathFiles = targetDirectory.listFiles();
        if(transmitFiles == null) {
            return false;
        }
        for(File file : transmitFiles) {
            Path targetPath = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit/" + file.getName()).toPath();
            try {
                //判断重名
                for(File targetDirectoryFile : targetPathFiles) {
                    if(targetDirectoryFile.getName().equals(file.getName())) {
                        //文件名追加时间戳
                        targetPath = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit/" + System.currentTimeMillis() + file.getName()).toPath();
                        break;
                    }
                }
                Files.copy(file.toPath(), targetPath);
                file.delete();
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private void exportCrashReport() {
        File crashLogDirectory = new File(getActivity().getDataDir() + "/files/crash/");
        if(!crashLogDirectory.isDirectory()) {
            Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.crash_not_log, 2500).show();
            return;
        }
        File[] logFiles = crashLogDirectory.listFiles();
        if(logFiles.length == 0) {
            Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), R.string.crash_not_log, 2500).show();
            return;
        }
        new Thread(() -> {
            File logZipFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/crashReport-" + System.currentTimeMillis() + ".zip");
            try {
                ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(logZipFile.toPath()));
                for(File logFile : logFiles) {
                    FileInputStream inputStream = new FileInputStream(logFile);
                    zipOutputStream.putNextEntry(new ZipEntry(logFile.getName()));
                    byte[] buffer = new byte[(int) logFile.length()];
                    inputStream.read(buffer);
                    zipOutputStream.write(buffer);
                    inputStream.close();
                    zipOutputStream.closeEntry();
                }
                zipOutputStream.flush();
                zipOutputStream.close();
                getActivity().runOnUiThread(() -> Snackbar.make(((NewMainActivity) getActivity()).getBinding().getRoot(), getString(R.string.text_export_to) + logZipFile.getName(), 5000).show());
            } catch (IOException e) {
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
    //todo 关于页

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.setting_fragment_list);
    }
}