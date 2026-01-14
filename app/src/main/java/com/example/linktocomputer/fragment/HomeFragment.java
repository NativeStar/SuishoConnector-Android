package com.example.linktocomputer.fragment;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.constant.States;
import com.example.linktocomputer.databinding.FragmentHomeBinding;
import com.example.linktocomputer.interfaces.IQRCodeDetected;
import com.example.linktocomputer.jsonClass.HandshakePacket;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.journeyapps.barcodescanner.SourceData;
import com.journeyapps.barcodescanner.camera.CameraManager;
import com.journeyapps.barcodescanner.camera.CameraSettings;
import com.journeyapps.barcodescanner.camera.PreviewCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class HomeFragment extends Fragment {
    private boolean viewInitialized = false;
    private FragmentHomeBinding binding;
    private ActivityResultLauncher<Intent> mediaProjectionRequestCallback;
    private CameraManager cameraManager;
    //二维码检测线程
    private Thread detectThread;
    private final String ipAddressRegexp = "^(((25[0-5]|2[0-4]d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d))))$";
    private final Logger logger = LoggerFactory.getLogger(HomeFragment.class);

    public HomeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaProjectionRequestCallback = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            logger.debug("Media project permission callback result");
            //防止没连上
            if(NewMainActivity.networkService != null && NewMainActivity.networkService.isConnected) {
                Intent data = result.getData();
                if(data == null) return;
                logger.info("Set media projection callback intent");
                NewMainActivity.networkService.setMediaProjectionIntent(data);
                binding.cardTextMediaProjectionMode.setText(R.string.text_authorized);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewInitialized = true;
        init();
    }

    public void setAutoConnecting(boolean autoConnecting) {
        //偶尔的空指针
        if(!viewInitialized) return;
        logger.debug("Update auto connection state display");
        binding.cardConnectionStateIcon.setImageResource(autoConnecting ? R.drawable.baseline_cell_tower_24 : R.drawable.baseline_signal_cellular_off_24);
        binding.cardTextConnectionStateSubtitle.setText(autoConnecting ? R.string.connection_auto_mode : R.string.text_connection_state_subtitle_not_connect);
    }

    /**
     * 初始化
     */
    private void init() {
        //卡片内连接按钮
        binding.buttonConnectMethodQrcode.setOnClickListener(v -> {
            if(!(getActivity().checkSelfPermission("android.permission.CAMERA") == PackageManager.PERMISSION_GRANTED)) {
                logger.info("Not camera permission,Show request dialog");
                new MaterialAlertDialogBuilder(getActivity()).setMessage(R.string.permission_scanCode_camera_message).setTitle(R.string.permission_request_alert_title).setPositiveButton(R.string.text_ok, (dialog, which) -> {
                    requestPermissions(new String[]{"android.permission.CAMERA"}, 1);
                }).setNegativeButton(R.string.text_cancel, (dialog, which) -> dialog.cancel()).show();
                return;
            }
            //防止重复调用连接
            if(checkConnected()) {
                Snackbar.make(binding.getRoot(), R.string.text_need_disconnect_first, 2000)
                        .setAction(R.string.text_disconnect, action -> {
                            ((NewMainActivity) getActivity()).showDisconnectOrCloseApplicationDialog();
                        })
                        .show();
                return;
            }
            Activity activity = getActivity();
            //停止接收广播 避免一堆bug
            NewMainActivity newMainActivity = (NewMainActivity) activity;
            if(newMainActivity.autoConnector != null) {
                logger.info("Stop auto connect listener by QRCode scanner");
                newMainActivity.autoConnector.stopListener();
                setAutoConnecting(false);
            }
            //拉起相机view
            logger.info("Show QRCode scan view");
            cameraManager = new CameraManager(activity);
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getActivity());
            View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_qrcode_scan, getActivity().findViewById(R.id.coordinatorLayout3), false);
            SurfaceHolder surfaceHolder = ((SurfaceView) bottomSheetView.findViewById(R.id.qrcode_scan_surface_view)).getHolder();
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    logger.debug("Camera surface created.Init camera");
                    CameraSettings settings = new CameraSettings();
                    settings.setBarcodeSceneModeEnabled(false);
                    settings.setAutoFocusEnabled(true);
                    settings.setRequestedCameraId(0);
                    cameraManager.setCameraSettings(settings);
                    try {
                        cameraManager.open();
                        cameraManager.configure();
                        //呃...修一个莫名其妙的bug
                        //预览翻转角度异常
                        Class<?> clazz = cameraManager.getClass();
                        Method method = clazz.getDeclaredMethod("setCameraDisplayOrientation", int.class);
                        method.setAccessible(true);
                        method.invoke(cameraManager, 90);
                        cameraManager.startPreview();
                        detectQrcode(result -> {
                            cameraManager.stopPreview();
                            cameraManager.close();
                            bottomSheetDialog.cancel();
                            readQRCodeContent(result.getText());
                        });
                        cameraManager.setPreviewDisplay(holder);
                    } catch (IOException | NoSuchMethodException | IllegalAccessException |
                             InvocationTargetException e) {
                        logger.error("Error when init camera", e);
                        bottomSheetDialog.cancel();
                        Snackbar.make(binding.getRoot(), R.string.error_camera_init, 2000).show();
                    }
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    logger.debug("Camera surface destroyed");
                    cameraManager.stopPreview();
                    cameraManager.close();
                }
            });
            bottomSheetDialog.setContentView(bottomSheetView);
            bottomSheetDialog.setCanceledOnTouchOutside(true);
            bottomSheetDialog.setOnCancelListener(dialog -> {
                logger.debug("QRCode scan view cancelled");
                cameraManager.stopPreview();
                cameraManager.close();
            });
            bottomSheetDialog.setOnDismissListener(dialog -> {
                logger.debug("QRCode scan view dismissed");
                cameraManager.stopPreview();
                cameraManager.close();
            });
            bottomSheetDialog.show();
        });
        //调试
        binding.cardConnectionStateIcon.setOnClickListener(v1 -> {
            if(0 == (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
                logger.debug("Not debug mode,Ignore debug menu create");
                return;
            }
            logger.debug("Show debug menu");
            new MaterialAlertDialogBuilder(getActivity())
                    .setItems(new CharSequence[]{
                            "Edit desktop client state",
                            "Finish activity",
                            "Throw exception",
                            "Edit state"
                    }, (dialog, which) -> {
                        dialog.dismiss();
                        if(which == 0) {
                            EditText editText = new EditText(getActivity());
                            editText.setHint("State id");
                            new MaterialAlertDialogBuilder(getActivity())
                                    .setView(editText)
                                    .setTitle("Edit desktop state")
                                    .setPositiveButton("Add", (dialog1, which1) -> {
                                        JsonObject jsonObject = new JsonObject();
                                        jsonObject.addProperty("packetType", "edit_state");
                                        jsonObject.addProperty("type", "add");
                                        jsonObject.addProperty("name", editText.getText().toString());
                                        GlobalVariables.computerConfigManager.getNetworkService().sendObject(jsonObject);
                                    })
                                    .setNegativeButton("Remove", (dialog1, which1) -> {
                                        JsonObject jsonObject = new JsonObject();
                                        jsonObject.addProperty("packetType", "edit_state");
                                        jsonObject.addProperty("type", "remove");
                                        jsonObject.addProperty("name", editText.getText().toString());
                                        GlobalVariables.computerConfigManager.getNetworkService().sendObject(jsonObject);
                                    })
                                    .setNeutralButton("Cancel", (dialog1, which1) -> {
                                    })
                                    .show();
                        } else if(which == 1) {
                            getActivity().finish();
                        } else if(which == 2) {
                            throw new RuntimeException("Test exception");
                        } else if(which == 3) {
                            EditText editText = new EditText(getActivity());
                            editText.setHint("State id");
                            new MaterialAlertDialogBuilder(getActivity())
                                    .setView(editText)
                                    .setTitle("Edit state")
                                    .setPositiveButton("Add", (dialog1, which1) -> ((NewMainActivity) getActivity()).stateBarManager.addState(States.getStateList().get(editText.getText().toString())))
                                    .setNegativeButton("Remove", (dialog1, which1) -> ((NewMainActivity) getActivity()).stateBarManager.removeState(States.getStateList().get(editText.getText().toString())))
                                    .setNeutralButton("Cancel", (dialog1, which1) -> {
                                    })
                                    .show();
                        }
                    })
                    .setTitle("Debug Menu")
                    .setPositiveButton("Close", null)
                    .show();
        });
        binding.buttonConnectMethodAddressInput.setOnClickListener(v -> {
            //防止重复调用连接
            if(checkConnected()) {
                Snackbar.make(binding.getRoot(), R.string.text_need_disconnect_first, 2000)
                        .setAction(R.string.text_disconnect, action -> {
                            ((NewMainActivity) getActivity()).showDisconnectOrCloseApplicationDialog();
                        })
                        .show();
                return;
            }
            //关闭广播
            NewMainActivity newMainActivity = (NewMainActivity) getActivity();
            if(newMainActivity.autoConnector != null) {
                logger.debug("Stop auto connect listener by manual connect");
                newMainActivity.autoConnector.stopListener();
                setAutoConnecting(false);
            }
            logger.debug("Show address input dialog");
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getActivity());
            View bottomSheetView = getLayoutInflater().inflate(R.layout.connect_input_address_bottom_sheet, getActivity().findViewById(R.id.coordinatorLayout3), false);
            bottomSheetView.findViewById(R.id.bottom_sheet_address_connect_button).setOnClickListener(callback -> {
                String userInputIP = ((TextInputEditText) bottomSheetView.findViewById(R.id.urlInput)).getText().toString();
                String userInputPort = ((TextInputEditText) bottomSheetView.findViewById(R.id.portInput)).getText().toString();
                String userInputPairCode = ((TextInputEditText) bottomSheetView.findViewById(R.id.pairCodeInput)).getText().toString();
                logger.debug("User input ip: " + userInputIP + " port: " + userInputPort + " pairCode: " + userInputPairCode);
                if(userInputIP.isEmpty()) {
                    //设置提示内容并给予输入框焦点
                    logger.debug("IP input empty");
                    ((TextInputEditText) bottomSheetView.findViewById(R.id.urlInput)).setError(getText(R.string.error_emptyInput_pleaseInputAddressHere));
                    bottomSheetView.findViewById(R.id.urlInput).requestFocus();
                    return;
                }
                //判断是否符合格式要求
                if(!Pattern.matches(ipAddressRegexp, userInputIP)) {
                    logger.debug("IP input invalid");
                    ((TextInputEditText) bottomSheetView.findViewById(R.id.urlInput)).setError(getText(R.string.ipAddressInput_invalid));
                    bottomSheetView.findViewById(R.id.urlInput).requestFocus();
                    return;
                }
                //端口号合规性
                try {
                    if(Integer.parseInt(userInputPort) > 65535) {
                        logger.debug("Port input invalid(too large)");
                        ((TextInputEditText) bottomSheetView.findViewById(R.id.portInput)).setError(getText(R.string.portInput_invalid));
                        ((TextInputEditText) bottomSheetView.findViewById(R.id.portInput)).setText("");
                        bottomSheetView.findViewById(R.id.portInput).requestFocus();
                        return;
                    }
                } catch (NumberFormatException exception) {
                    logger.debug("Port input invalid(not number)");
                    /*当输入为空字符串时触发*/
                    ((TextInputEditText) bottomSheetView.findViewById(R.id.portInput)).setError(getText(R.string.portInput_invalid));
                    ((TextInputEditText) bottomSheetView.findViewById(R.id.portInput)).setText("");
                    bottomSheetView.findViewById(R.id.portInput).requestFocus();
                    return;
                }
                //配对码
                if(userInputPairCode.length() != 6) {
                    logger.debug("Pair code input invalid");
                    ((TextInputEditText) bottomSheetView.findViewById(R.id.pairCodeInput)).setError(getText(R.string.pairCodeInput_invalid));
                    bottomSheetView.findViewById(R.id.pairCodeInput).requestFocus();
                    return;
                }
                bottomSheetDialog.cancel();
                ((NewMainActivity) getActivity()).connectByAddressInput(userInputIP, userInputPort, userInputPairCode, null);
            });
            bottomSheetDialog.setContentView(bottomSheetView);
            bottomSheetDialog.setCanceledOnTouchOutside(true);
            bottomSheetDialog.show();
        });
        //关闭连接
        binding.homeDisconnectActionButton.setOnClickListener(v -> ((NewMainActivity) getActivity()).showDisconnectOrCloseApplicationDialog());
        //信任模式
        binding.cardTrustModeClickable.setOnClickListener(v -> {
            NewMainActivity activity = (NewMainActivity) getActivity();
            if(activity == null || !activity.isServerConnected()) {
                return;
            }
            logger.info("Show change trust mode dialog");
            boolean trusted = GlobalVariables.computerConfigManager.isTrustedComputer();
            logger.debug("Computer current is trusted: {}", trusted);
            //构建对话框消息
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(activity.getResources().getString(R.string.dialog_change_trust_mode_message));
            stringBuilder.append(trusted ? activity.getResources().getString(R.string.text_untrusted) : activity.getResources().getString(R.string.text_trust));
            stringBuilder.append("?\n");
            stringBuilder.append(activity.getResources().getString(R.string.dialog_change_trust_mode_message2));
            //询问
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.dialog_change_trust_mode_title)
                    .setMessage(stringBuilder)
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                    })
                    .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                        GlobalVariables.computerConfigManager.changeTrustMode();
                        logger.debug("Change trust mode to {}", !trusted);
                        activity.updateConnectionStateDisplay();
                    }).show();
        });
        //音频转发授权
        binding.cardMediaProjectionClickable.setOnClickListener(v -> {
            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                Snackbar.make(binding.getRoot(), R.string.text_android_version_not_support, Snackbar.LENGTH_LONG).show();
                return;
            }
            NewMainActivity activity = (NewMainActivity) getActivity();
            if(activity == null) {
                return;
            }
            ConnectMainService networkService = NewMainActivity.networkService;
            if(networkService == null) {
                Snackbar.make(binding.getRoot(), R.string.text_need_connect_first, Snackbar.LENGTH_LONG).show();
                return;
            }
            if(networkService.getMediaProjectionServiceIntent() != null) {
                logger.debug("Already has media projection permission");
                Snackbar.make(binding.getRoot(), R.string.text_authorized, Snackbar.LENGTH_LONG).show();
                return;
            }
            if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.permission_request_alert_title)
                        .setMessage(R.string.permission_audio_forward_message)
                        .setNeutralButton(R.string.text_cancel, (dialog, which) -> {
                        })
                        .setPositiveButton(R.string.text_ok, (dialog, which) -> activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1))
                        .show();
                return;
            }
            MediaProjectionManager manager = getActivity().getSystemService(MediaProjectionManager.class);
            if(manager == null) {
                logger.warn("Filed to get media projection manager");
                Snackbar.make(binding.getRoot(), R.string.error_media_projection_manager_null, Snackbar.LENGTH_LONG).show();
                return;
            }
            logger.debug("Request media projection permission");
            Intent intent = manager.createScreenCaptureIntent();
            mediaProjectionRequestCallback.launch(intent);
        });
    }

    private void detectQrcode(IQRCodeDetected runnable) {
        detectThread = new Thread(() -> {
            while (cameraManager.isOpen() && !detectThread.isInterrupted()) {
                cameraManager.requestPreviewFrame(new PreviewCallback() {
                    @Override
                    public void onPreview(SourceData sourceData) {
                        sourceData.setCropRect(new Rect(0, 0, sourceData.getDataWidth(), sourceData.getDataHeight()));
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(sourceData.createSource()));
                        QRCodeReader reader = new QRCodeReader();
                        try {
                            Result result = reader.decode(bitmap);
                            if(result != null) {
                                detectThread.interrupt();
                                cameraManager.stopPreview();
                                cameraManager.close();
                                runnable.onDetected(result);
                            }
                        } catch (NotFoundException | ChecksumException | FormatException ignored) {
                        }
                    }

                    @Override
                    public void onPreviewError(Exception ignore) {
                        cameraManager.stopPreview();
                        cameraManager.close();
                    }
                });
            }
        });
        detectThread.start();
    }

    private void readQRCodeContent(String content) {
        try {
            //判断是否误扫pc端上的下载二维码
            //如果是 直接启动浏览器
            if(content.endsWith("/suishoPkgDownload")) {
                logger.info("User scanned package download QRCode");
                new MaterialAlertDialogBuilder(getActivity())
                        .setTitle(getActivity().getResources().getString(R.string.text_connect_failed))
                        .setMessage(getActivity().getResources().getString(R.string.dialog_scanned_wrong_qrcode))
                        .setPositiveButton(R.string.text_open_in_browser, (dialog, which) -> {
                            logger.info("Open download url in browser");
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(content));
                            startActivity(intent);
                        }).setNegativeButton(R.string.text_cancel, (dialog, which) -> dialog.dismiss())
                        .setNeutralButton(R.string.text_direct_download, (dialog, which) -> {
                            logger.info("Download package with system download manager");
                            DownloadManager downloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
                            //下载地址和访问地址不一样 替换
                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(content.replace("suishoPkgDownload", "dlPackage")));
                            request.setTitle(getString(R.string.app_name));
                            request.setDescription(getString(R.string.direct_download_desc));
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SuishoConnectorPackage.apk");
                            request.setMimeType("application/vnd.android.package-archive");
                            downloadManager.enqueue(request);
                        })
                        .setCancelable(false)
                        .show();
                return;
            }
            HandshakePacket jsonObject = GlobalVariables.jsonBuilder.fromJson(content, HandshakePacket.class);
            if(jsonObject.id.length() != 32) throw new Exception("Invalid QRCode");
            ((NewMainActivity) getActivity()).connectByQRCode(jsonObject.address, jsonObject.port, jsonObject.id, jsonObject.certDownloadPort, jsonObject.token);
        } catch (Exception e) {
            logger.warn("Invalid QRCode", e);
            new MaterialAlertDialogBuilder(getActivity())
                    .setTitle(getActivity().getResources().getString(R.string.text_connect_failed))
                    .setMessage(getActivity().getResources().getString(R.string.text_invalid_qrcode))
                    .setCancelable(false)
                    .setNegativeButton(R.string.text_ok, (dialog, which) -> dialog.dismiss()).show();
        }
    }

    //检查是否连接 再次包装
    private boolean checkConnected() {
        return ((NewMainActivity) getActivity()).isServerConnected();
    }
}