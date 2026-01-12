package com.example.linktocomputer.activity;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.abstracts.TransmitMessageAbstract;
import com.example.linktocomputer.constant.States;
import com.example.linktocomputer.databinding.ActivityConnectedBinding;
import com.example.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.example.linktocomputer.fragment.TransmitFragment;
import com.example.linktocomputer.instances.ComputerConfigManager;
import com.example.linktocomputer.instances.StateBarManager;
import com.example.linktocomputer.instances.adapter.HomeViewPagerAdapter;
import com.example.linktocomputer.interfaces.IConnectedActivityMethods;
import com.example.linktocomputer.jsonClass.ManualConnectPacket;
import com.example.linktocomputer.network.NetworkStateCallback;
import com.example.linktocomputer.network.NetworkUtil;
import com.example.linktocomputer.network.udp.AutoConnector;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NewMainActivity extends AppCompatActivity {

    public ActivityConnectedBinding getBinding() {
        return binding;
    }

    private ActivityConnectedBinding binding;
    //仅ip地址校验正则
    public static ConnectMainService networkService = null;
    public HomeViewPagerAdapter viewPagerAdapter = null;
    public StateBarManager stateBarManager;
    private static Intent networkServiceIntent = null;
    private AlertDialog connectingDialogInstance = null;
    //wifi状态监听回调
    private NetworkStateCallback networkStateCallback;
    private ServiceConnection networkServiceConnection;
    private final int[] navigationIds = {R.id.connected_activity_navigation_bar_menu_home, R.id.connected_activity_navigation_bar_menu_transmit, R.id.connected_activity_navigation_bar_menu_setting};
    public AutoConnector autoConnector;
    private boolean autoConnectorWorked = false;
    private final Logger logger = LoggerFactory.getLogger(NewMainActivity.class);

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.debug("onCreate called");
        binding = ActivityConnectedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        //状态提示
        setSupportActionBar(binding.toolbar);
        //通知栏色彩
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        //设置
        GlobalVariables.settings = getSharedPreferences("settings", MODE_PRIVATE);
        //将id设为全局变量
        GlobalVariables.androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        //初始化viewpager
        setViewsInteraction();
        //activity被回收
        if(networkService != null && networkService.isConnected) {
            logger.debug("Has network service.Resuming state");
            bindNetworkService();
            showConnectedState();
        }
        //返回键事件 高版本安卓用
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            logger.debug("Registering onBackInvokedCallback");
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if(networkService == null || !networkService.isConnected) {
                    logger.debug("onBackInvokedCallback called.Show exit confirm dialog");
                    new MaterialAlertDialogBuilder(this)
                            .setMessage("当前未连接任何设备\n你是希望退出程序还是希望其继续后台运行?")
                            .setPositiveButton("后台运行", (dialog, which) -> {
                                logger.debug("onBackInvokedCallback called.Move task to back");
                                dialog.dismiss();
                                moveTaskToBack(true);
                            })
                            .setNegativeButton("退出", (dialog, which) -> {
                                logger.debug("onBackInvokedCallback called.Exit");
                                finishAffinity();
                                System.exit(0);
                            })
                            .show();
                } else {
                    logger.debug("onBackInvokedCallback called.Move task to back by has connection");
                    moveTaskToBack(true);
                }
            });
        }
    }


    @Override
    public void onBackPressed() {
        //低版本安卓可能有用
        //如果未进行任何连接 弹出提示
        if(networkService == null || !networkService.isConnected) {
            logger.debug("onBackPressed called.Show exit confirm dialog");
            new MaterialAlertDialogBuilder(this)
                    .setMessage("当前未连接任何设备\n你是希望退出程序还是希望其继续后台运行?")
                    .setPositiveButton("后台运行", (dialog, which) -> {
                        logger.debug("onBackPressed called.Move task to back");
                        dialog.dismiss();
                        moveTaskToBack(true);
                    })
                    .setNegativeButton("退出", (dialog, which) -> {
                        logger.debug("onBackPressed called.Exit");
                        finishAffinity();
                        System.exit(0);
                    })
                    .show();
        } else {
            logger.debug("onBackPressed called.Move task to back by has connection");
            moveTaskToBack(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_menu, menu);
        //应该够创建view了
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                stateBarManager.init();
            }
        }, 500);
        logger.debug("onCreateOptionsMenu called");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        stateBarManager.onMenuClick();
        return super.onOptionsItemSelected(item);
    }

    private void setViewsInteraction() {
        logger.debug("Init views interaction");
        stateBarManager = new StateBarManager(this);
        //fragment适配器
        viewPagerAdapter = new HomeViewPagerAdapter(this);
        binding.homeViewPager2.setAdapter(viewPagerAdapter);
        //如果是重新创建就不在这设置了
        binding.homeViewPager2.setOffscreenPageLimit(1);
        viewPagerAdapter.getTransmitFragment().setActivity(this);
        //尝试修复进软件初始化失败
        binding.getRoot().post(() -> {
            //点击导航栏更改fragment显示
            binding.connectedActivityNavigationBar.setOnItemSelectedListener(item -> {
                int selectedId = item.getItemId();
                if(selectedId == R.id.connected_activity_navigation_bar_menu_home) {
                    binding.homeViewPager2.setCurrentItem(0);
                } else if(selectedId == R.id.connected_activity_navigation_bar_menu_transmit) {
                    if(binding.homeViewPager2.getCurrentItem() == 1) {
                        //到底部
                        viewPagerAdapter.getTransmitFragment().scrollMessagesViewToBottom(true);
                        logger.debug("Transmit page scroll to bottom by navigation double click");
                    } else {
                        binding.homeViewPager2.setCurrentItem(1);
                    }
                } else {
                    binding.homeViewPager2.setCurrentItem(2);
                }
                return true;
            });
            //禁止左右滑动
            binding.homeViewPager2.setUserInputEnabled(false);
            //滑动viewPager修改下方显示
            binding.homeViewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    binding.connectedActivityNavigationBar.setSelectedItemId(navigationIds[position]);
                }
            });
            System.gc();
            checkState();
            if(GlobalVariables.preferences.getBoolean("function_launch_verify", false)) {
                logger.debug("User enabled launch verify");
                ((TextView) findViewById(R.id.card_text_connection_state_subtitle)).setText(R.string.card_text_waiting_verify);
                AlertDialog verifyDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.auth_dialog_title)
                        .setMessage(R.string.auth_launch_verify_dialog_message)
                        .setCancelable(false)
                        .setNeutralButton(R.string.text_exit, null)
                        .setPositiveButton(R.string.text_verify, null)
                        .create();
                verifyDialog.show();
                //手动验证
                verifyDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    logger.debug("Clicked dialog verify button");
                    showLaunchVerifyPrompt(verifyDialog);
                });
                //退出
                verifyDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    if(networkService == null || !networkService.isConnected) {
                        logger.debug("User cancelled verify with not connection.Exiting");
                        finishAffinity();
                        System.exit(0);
                    } else {
                        logger.debug("User cancelled verify with connection.Move task to back");
                        moveTaskToBack(true);
                    }
                });
                //启动时自动发起验证
                showLaunchVerifyPrompt(verifyDialog);
            } else {
                if(!autoConnectorWorked) initAutoConnect();
            }
            //显示设备AndroidId
            ((TextView) findViewById(R.id.card_text_phone_id)).setText(GlobalVariables.androidId);
        });
    }

    private void showLaunchVerifyPrompt(DialogInterface dialog) {
        logger.info("Showed launch verify prompt");
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.auth_dialog_title))
                .setSubtitle(getString(R.string.auth_dialog_desc))
                .setDeviceCredentialAllowed(true)
                .build();
        prompt.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                dialog.dismiss();
                Snackbar.make(getBinding().getRoot(), R.string.text_verify_success, 1500).show();
                if(!autoConnectorWorked) {
                    logger.debug("User verify success and enabled auto connect");
                    initAutoConnect();
                } else {
                    ((TextView) findViewById(R.id.card_text_connection_state_subtitle)).setText(R.string.text_connection_state_subtitle_not_connect);
                }
            }
        });
    }

    //只在启动应用时开启 避免点击断连接结果又自动连上的奇葩情况
    private void initAutoConnect() {
        //检测是否已有绑定设备且目前未连接任何设备
        if(GlobalVariables.settings.getBoolean("boundDevice", false) && (networkService == null || !networkService.isConnected)) {
            logger.debug("Auto connect enabled and not connection");
            //网络检测
            if(!NetworkUtil.checkNetworkUsable(this)) {
                //提示
                stateBarManager.addState(States.getStateList().get("info_auto_connect_not_wifi"));
                logger.debug("Auto connect not wifi.Returned");
                return;
            }
            //更改view
            viewPagerAdapter.getHomeFragment().setAutoConnecting(true);
            //接收udp广播
            WifiManager manager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
            WifiManager.MulticastLock lock = manager.createMulticastLock("udp");
            lock.acquire();
            if(autoConnector == null) {
                //刚启动
                autoConnector = new AutoConnector(getFilesDir() + "/bind.key", this, lock);
                autoConnector.start();
                logger.info("Receiving auto connect broadcast");
            } else if(autoConnector.isWorking()) {
                //activity被销毁重建 更新它
                logger.debug("Updating auto connect broadcaster activity");
                autoConnector.setActivity(this);
            }
            //监听网络状态更改
            ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
            networkStateCallback = new NetworkStateCallback(this);
            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), networkStateCallback);
        }
    }

    private void bindNetworkService() {
        logger.debug("Binding network service");
        networkServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                logger.info("Network service connected");
                ConnectMainService.MyBinder myBinder = (ConnectMainService.MyBinder) service;
                networkService = myBinder.getService();
                networkService.showForegroundNotification(NewMainActivity.this);
                viewPagerAdapter.getTransmitFragment().setNetworkService(networkService);
                //services调用activity等方法的接口
                GlobalVariables.networkServiceBound = true;
                networkService.setActivityMethods(new IConnectedActivityMethods() {
                    @Override
                    public void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract data, boolean requestSave) {
                        logger.debug("Adding item to transmit fragment");
                        runOnUiThread(() -> {
                            HomeViewPagerAdapter homeViewPagerAdapter = (HomeViewPagerAdapter) binding.homeViewPager2.getAdapter();
                            TransmitFragment transmitFragment = homeViewPagerAdapter.getTransmitFragment();
                            transmitFragment.addItem(type, data, requestSave, false);
                        });
                    }

                    @Override
                    public void showAlert(String title, String content, String buttonText) {
                        runOnUiThread(() -> {
                            if(isFinishing()) return;
                            logger.debug("Showing alert from network service(String)");
                            new MaterialAlertDialogBuilder(NewMainActivity.this)
                                    .setTitle(title)
                                    .setMessage(content)
                                    .setPositiveButton(buttonText, (dialog, which) -> {
                                    }).show();
                        });
                    }

                    @Override
                    public void showAlert(int title, int content, int buttonText) {
                        runOnUiThread(() -> {
                            if(isFinishing()) return;
                            logger.debug("Showing alert from network service(ResId)");
                            new MaterialAlertDialogBuilder(NewMainActivity.this)
                                    .setTitle(getText(title))
                                    .setMessage(getText(content))
                                    .setPositiveButton(getText(buttonText), (dialog, which) -> {
                                    }).show();
                        });
                    }

                    public void showConnectingDialog() {
                        runOnUiThread(() -> {
                            if(isFinishing()) return;
                            connectingDialogInstance = new MaterialAlertDialogBuilder(getActivity())
                                    .setView(R.layout.connecting_dialog)
                                    .setCancelable(false)
                                    .create();
                            connectingDialogInstance.show();
                        });
                    }

                    @Override
                    public void closeConnectingDialog() {
                        if(connectingDialogInstance != null) connectingDialogInstance.dismiss();
                    }

                    public NewMainActivity getActivity() {
                        return NewMainActivity.this;
                    }

                    public TransmitFragment getTransmitFragment() {
                        return viewPagerAdapter.getTransmitFragment();
                    }

                    @Override
                    public void onConnected(String sessionId) {
                        logger.info("Service connected.Init views");
                        GlobalVariables.computerConfigManager = new ComputerConfigManager(GlobalVariables.computerName, GlobalVariables.computerId, NewMainActivity.this, networkService, sessionId);
                        GlobalVariables.computerConfigManager.init(null);
                        //设置
                        showConnectedState();
                        //首次连接的设备 询问是否信任
                        if(GlobalVariables.computerConfigManager.isFirstConnect() && !GlobalVariables.computerConfigManager.isTrustedComputer()) {
                            showTrustModeDialog();
                        }
                        //移除相关状态
                        stateBarManager.removeState(States.getStateList().get("error_auto_connect"));
                        stateBarManager.removeState(States.getStateList().get("info_auto_connect_not_wifi"));
                        //释放
                        unregisterNetworkCallback();
                        autoConnector = null;
                    }

                    @Override
                    public void onDisconnect() {
                        networkService = null;
                        logger.info("Service disconnected.Init views");
                        runOnUiThread(() -> {
                            if(isFinishing() || isDestroyed()) return;
                            //恢复显示
                            try {
                                ((TextView) findViewById(R.id.card_text_computer_id)).setText(R.string.text_not_connect);
                                ((TextView) findViewById(R.id.card_text_connection_state)).setText(R.string.text_not_connect);
                                ((TextView) findViewById(R.id.card_text_connection_state_subtitle)).setText(R.string.text_connection_state_subtitle_not_connect);
                                ((ImageView) findViewById(R.id.card_connection_state_icon)).setImageResource(R.drawable.baseline_signal_cellular_off_24);
                                ((TextView) findViewById(R.id.card_text_notification_forward)).setText(R.string.text_not_connect);
                                ((TextView) findViewById(R.id.card_text_trust_mode)).setText(R.string.text_not_connect);
                                ((TextView) findViewById(R.id.card_text_file_manager)).setText(R.string.text_not_connect);
                                ((TextView) findViewById(R.id.card_title_trust_mode)).setText(R.string.home_card_trust_mode);
                                ((FloatingActionButton) findViewById(R.id.home_disconnect_action_button)).setImageResource(R.drawable.baseline_close_24);
                                ((TextView) findViewById(R.id.card_text_media_projection_mode)).setText(R.string.text_unauthorized);
                            } catch (NullPointerException e) {
                                logger.error("Error when init views",e);
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onNotificationListenerServiceConnect() {
                        //不知道为啥因为nullptr崩了
                        TextView notificationStateText = findViewById(R.id.card_text_notification_forward);
                        if(notificationStateText == null) return;
                        logger.info("Notification service connected to network service.Init views");
                        //判断有无权限
                        if(networkService.checkNotificationListenerPermission()) {
                            notificationStateText.setText(R.string.text_running);
                        } else {
                            stateBarManager.addState(States.getStateList().get("info_notification_listener_permission"));
                            notificationStateText.setText(R.string.text_not_permission);
                        }
                    }
                });
                //连接
                if(networkService != null && !networkService.isConnected) {
                    logger.info("Call network service connect");
                    networkService.connect(networkServiceIntent.getStringExtra("url"));
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.info("Network service disconnected");
                GlobalVariables.networkServiceBound = false;
            }
        };
        bindService(networkServiceIntent, networkServiceConnection, BIND_IMPORTANT);
    }

    /**
     * 断开连接对话框
     */
    public void showDisconnectOrCloseApplicationDialog() {
        //检查是否有连接
        if(!isServerConnected()) {
            //无连接 关闭程序
            logger.debug("No connection.Show close application dialog");
            new MaterialAlertDialogBuilder(this)
//                    .setTitle(R.string.dialog_close_application_confirm_title)
                    .setMessage(R.string.dialog_close_application_confirm_message)
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                    })
                    .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                        //确保有退出的过渡动画而不是闪一下
                        moveTaskToBack(false);
                        Process.killProcess(Process.myPid());
                    })
                    .show();
            return;
        }
        logger.debug("Has connection.Show disconnect dialog");
        new MaterialAlertDialogBuilder(this)
                .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                    if(networkService != null) {
                        networkService.disconnect();
                        Snackbar.make(binding.getRoot(), R.string.text_disconnect_action_success, 2000).show();
                        dialog.cancel();
                    }
                })
                .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                    dialog.cancel();
                })
                .setMessage(R.string.text_disconnect_confirm_message)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.debug("Main activity destroy");
        //解绑服务
        if(networkServiceConnection != null) unbindService(networkServiceConnection);
        if(autoConnector != null) {
            logger.debug("Stop auto connector listener");
            autoConnector.stopListener();
        }
    }
    /**
     * 检查保活相关并提醒
     */
    public void checkSomePermissionAndShowTips() {
        //电池优化
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if(powerManager == null || !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            logger.info("Battery optimization not ignored.Add state");
            stateBarManager.addState(States.getStateList().get("info_battery_opt"));
        } else {
            //在更改完权限时更新
            logger.debug("Battery optimization ignored.Remove state");
            stateBarManager.removeState(States.getStateList().get("info_battery_opt"));
        }
        //通知权限
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(!notificationManager.areNotificationsEnabled()) {
            logger.info("Not notification permission.Add state");
            stateBarManager.addState(States.getStateList().get("warn_send_notification"));
        } else {
            logger.debug("Has notification permission.Remove state");
            stateBarManager.removeState(States.getStateList().get("warn_send_notification"));
        }
        //通知转发权限提示更新
        if(checkNotificationListenerPermission()) {
            logger.debug("Has notification listener permission.Remove state");
            stateBarManager.removeState("info_notification_listener_permission");
        } else {
            logger.info("No notification listener permission.Add state");
            stateBarManager.addState(States.getStateList().get("info_notification_listener_permission"));
        }
    }

    /**
     * 检查状态并设置提示
     */
    private void checkState() {
        //通知监听
        //如果未开启转发功能 则忽略
        if(!GlobalVariables.preferences.getBoolean("function_notification_forward", false)){
            logger.debug("Notification forward disabled.Ignore notification listener permission recheck");
            return;
        }
        if(!checkNotificationListenerPermission()) {
            logger.info("No notification listener permission in recheck.Add state");
            stateBarManager.addState(States.getStateList().get("info_notification_listener_permission"));
        }
        //
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.debug("Main activity resume");
        checkSomePermissionAndShowTips();
    }

    /**
     * 连接成功时显示状态
     */
    public void showConnectedState() {
        logger.debug("Update main activity connection state display");
        runOnUiThread(() -> {
            if(isFinishing() || isDestroyed()) return;
            //检查view是否完成初始化
            if(findViewById(R.id.card_text_computer_id) == null) {
                //等待完成自动执行
                binding.getRoot().post(this::showConnectedState);
                logger.debug("Waiting for view initialization");
                return;
            }
            boolean isTrusted = GlobalVariables.computerConfigManager.isTrustedComputer();
            //文本
            ((TextView) findViewById(R.id.card_text_computer_id)).setText(GlobalVariables.computerId);
            ((TextView) findViewById(R.id.card_text_connection_state)).setText(R.string.text_connected);
            ((TextView) findViewById(R.id.card_text_connection_state_subtitle)).setText(GlobalVariables.computerName);
            ((TextView) findViewById(R.id.card_title_trust_mode)).setText(R.string.home_card_trust_mode_connected);
            ((TextView) findViewById(R.id.card_text_media_projection_mode)).setText(networkService.getMediaProjectionServiceIntent() == null ? R.string.text_unauthorized : R.string.text_authorized);
            //图标
            ((FloatingActionButton) findViewById(R.id.home_disconnect_action_button)).setImageResource(R.drawable.baseline_link_off_24);
            ((ImageView) findViewById(R.id.card_connection_state_icon)).setImageResource(R.drawable.baseline_signal_cellular_4_bar_24);
            //通知转发 和信任
            if(isTrusted) {
                ((TextView) findViewById(R.id.card_text_trust_mode)).setText(R.string.text_trust);
                //通知转发
                if(networkService.getNotificationListenerWorking()) {
                    ((TextView) findViewById(R.id.card_text_notification_forward)).setText(R.string.text_running);
                } else {
                    if(networkService.checkNotificationListenerPermission()) {
                        ((TextView) findViewById(R.id.card_text_notification_forward)).setText(R.string.text_not_running);
                    } else {
                        ((TextView) findViewById(R.id.card_text_notification_forward)).setText(R.string.text_not_permission);
                    }
                }
                //文件管理
                if(!GlobalVariables.preferences.getBoolean("function_file_manager", false)) {
                    ((TextView) findViewById(R.id.card_text_file_manager)).setText(R.string.text_not_running);
                } else {
                    //权限
                    if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        if(!Environment.isExternalStorageManager()) {
                            ((TextView) findViewById(R.id.card_text_file_manager)).setText(R.string.text_not_permission);
                            return;
                        }
                    } else {
                        if(checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED || checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                            ((TextView) findViewById(R.id.card_text_file_manager)).setText(R.string.text_not_permission);
                            return;
                        }
                    }
                    ((TextView) findViewById(R.id.card_text_file_manager)).setText(R.string.text_running);

                }
            } else {
                //不信任的设备
                ((TextView) findViewById(R.id.card_text_trust_mode)).setText(R.string.text_untrusted);
                ((TextView) findViewById(R.id.card_text_file_manager)).setText(R.string.text_untrusted);
                ((TextView) findViewById(R.id.card_text_notification_forward)).setText(R.string.text_untrusted);
            }

        });
    }

    private void showTrustModeDialog() {
        if(isFinishing() || isDestroyed()) return;
        logger.debug("Show trust mode change dialog");
        runOnUiThread(() -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_trust_computer_title)
                    .setMessage(R.string.dialog_trust_computer_message)
                    .setPositiveButton(R.string.text_trust, (dialog, which) -> {
                        GlobalVariables.computerConfigManager.setTrusted(true);
                        ((TextView) findViewById(R.id.card_text_trust_mode)).setText(R.string.text_trust);
                    })
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                        //用来保存
                        GlobalVariables.computerConfigManager.setTrusted(false);
                        //默认显示不信任 没必要改了
                    })
                    .show();
        });
    }

    public void connectByQRCode(String address, int port, String computerId, int certDownloadPort, String pairToken) {
        String url = address + ":" + port;
        String ipAddressAndPortRegexp = "^(((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))):([1-9]|[1-9]\\d{1,3}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$";
        logger.info("Scan QRCode got url: {}", url);
        if(!Pattern.matches(ipAddressAndPortRegexp, url)) {
            //ip校验不通过 显示对话框
            logger.info("Invalid QRCode url");
            new MaterialAlertDialogBuilder(this)
                    .setTitle("连接失败")
                    .setMessage("无效二维码")
                    .setCancelable(false)
                    .setNegativeButton(R.string.text_ok, (dialog, which) -> dialog.dismiss()).show();
            return;
        }
        networkServiceIntent = new Intent(this, ConnectMainService.class);
        //传入地址
        networkServiceIntent.putExtra("url", "wss://" + url);
        //计算机id
        networkServiceIntent.putExtra("id", computerId);
        //证书下载端口
        networkServiceIntent.putExtra("certDownloadPort", certDownloadPort);
        //配对token
        networkServiceIntent.putExtra("pairToken", pairToken);
        //地址
        networkServiceIntent.putExtra("address", address);
        //emm虽然没什么意义
        Util.checkComponentEnable(this, ConnectMainService.class, true);
        logger.info("Start network service by QRCode scan");
        startService(networkServiceIntent);
        bindNetworkService();
    }

    //手动连接
    public void connectByAddressInput(String url, String port, @Nullable String pairCode, @Nullable String key) {
        new Thread(() -> {
            //使用预制证书加密 好过明文裸奔
            SSLContext manualConnectSSLContext;
            TrustManager manualConnectTrustManager;
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null);
                String certificateAlias = Integer.toString(0);
                keyStore.setCertificateEntry(certificateAlias, certificateFactory.generateCertificate(getResources().openRawResource(R.raw.default_cert)));
                manualConnectSSLContext = SSLContext.getInstance("TLS");
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);
                manualConnectTrustManager = trustManagerFactory.getTrustManagers()[0];
                manualConnectSSLContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            } catch (CertificateException | IOException | NoSuchAlgorithmException |
                     KeyStoreException | KeyManagementException e) {
                logger.error("Failed to load default cert",e);
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle("连接失败")
                        .setMessage("SSL证书读取异常:" + e)
                        .setCancelable(false)
                        .setNegativeButton(R.string.text_ok, (dialog, which) -> dialog.dismiss()).show());
                return;
            }
            Request.Builder requestBuilder = new Request.Builder()
                    .url("https://" + url + ":" + port)
                    .removeHeader("User-Agent")
                    .addHeader("User-Agent", "Shamiko")
                    .removeHeader("Accept-Encoding")
                    .addHeader("Accept-Encoding", "identity");
            if(pairCode != null) {
                logger.debug("Using pair code to connect");
                requestBuilder.addHeader("suisho-pair-code", pairCode);
            } else if(key != null) {
                logger.debug("Using key to connect");
                requestBuilder.addHeader("suisho-auto-connector-key", key);
            }
            Request request = requestBuilder.build();
            OkHttpClient client = new OkHttpClient()
                    .newBuilder()
                    .sslSocketFactory(manualConnectSSLContext.getSocketFactory(), (X509TrustManager) manualConnectTrustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
            logger.info("Sending manual connect request");
            try (Response response = client.newCall(request).execute()) {
                ManualConnectPacket packet = GlobalVariables.jsonBuilder.fromJson(response.body().string(), ManualConnectPacket.class);
                if(!packet.success) {
                    logger.info("Manual connect failed with message: {}", packet.message);
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setTitle("连接失败")
                            .setMessage(packet.message)
                            .setCancelable(false)
                            .setNegativeButton(R.string.text_ok, (dialog, which) -> dialog.dismiss()).show()
                    );
                    return;
                }
                runOnUiThread(() -> {
                    Snackbar.make(binding.getRoot(), "已发起连接", 1500).show();
                });
                logger.debug("Call QRCode connect by manual connect response data");
                //懒
                connectByQRCode(url, packet.mainPort, packet.id, packet.certPort, packet.token);
            } catch (IOException | NullPointerException e) {
                logger.error("Manual connect failed",e);
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle("连接失败")
                        .setMessage(e.toString())
                        .setCancelable(false)
                        .setNegativeButton(R.string.text_ok, (dialog, which) -> dialog.dismiss()).show());
            }
        }).start();
    }

    public boolean checkNotificationListenerPermission() {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(this);
        return packageNames.contains(getApplicationContext().getPackageName());
    }

    public boolean isServerConnected() {
        if(networkService == null) {
            return false;
        }
        return networkService.isConnected;
    }

    public void sendPacket(JsonObject object) {
        logger.debug("Activity sent a packet");
        if(networkService != null) networkService.sendObject(object);
    }

    public void setAutoConnectorWorked() {
        this.autoConnectorWorked = true;
    }

    //注销网络回调
    public void unregisterNetworkCallback() {
        if(networkStateCallback != null) {
            logger.debug("Unregistering network callback");
            try {
                ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
                connectivityManager.unregisterNetworkCallback(networkStateCallback);
            } catch (IllegalArgumentException ignore) {
            } finally {
                networkStateCallback = null;
            }
        }
    }
}
