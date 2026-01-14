package com.example.linktocomputer.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.IMediaProjectionServiceIPC;
import com.example.linktocomputer.R;
import com.example.linktocomputer.abstracts.FileUploadStateHandle;
import com.example.linktocomputer.abstracts.RequestHandle;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.constant.FileManagerResultCode;
import com.example.linktocomputer.constant.States;
import com.example.linktocomputer.enums.ComputerTrustMode;
import com.example.linktocomputer.enums.ConnectionCloseCode;
import com.example.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.example.linktocomputer.instances.EncryptionKey;
import com.example.linktocomputer.instances.transmit.TransmitMessageTypeText;
import com.example.linktocomputer.interfaces.IConnectedActivityMethods;
import com.example.linktocomputer.interfaces.INetworkService;
import com.example.linktocomputer.jsonClass.MainServiceJson;
import com.example.linktocomputer.network.FileServer;
import com.example.linktocomputer.network.TransmitDownloadFile;
import com.example.linktocomputer.network.TransmitUploadFile;
import com.example.linktocomputer.receiver.BatteryStateReceiver;
import com.example.linktocomputer.responseBuilders.AllPackageResponse;
import com.example.linktocomputer.responseBuilders.CurrentNotificationsListPacket;
import com.example.linktocomputer.responseBuilders.DetailBuilder;
import com.example.linktocomputer.responseBuilders.HandshakeResponse;
import com.example.linktocomputer.responseBuilders.EmptyResponsePacketBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ConnectMainService extends Service implements INetworkService {
    //ws客户端
    WebSocket webSocketClient;
    //是否已完成连接
    //是否完成首次握手
    private boolean isHandShaken = false;
    private final Map<String, RequestHandle> requestMapping = new HashMap<>();
    private String computerId;
    //证书下载端口
    private int certDownloadPort;
    //计算机地址
    private String computerAddress;
    private String pairToken;
    public IConnectedActivityMethods activityMethods;
    //通知监听服务运行状态
    private boolean notificationListenerServiceWorking = false;
    //电池状广播接收器
    BatteryStateReceiver batteryStateReceiver = null;
    private BroadcastReceiver closeConnectionBroadcastReceiver = null;
    private ServiceConnection bindServiceConnection = null;
    //文件管理器 远程播放流媒体等的文件服务器
    public final FileServer webFileServer = new FileServer(30767);
    //投屏同意返回intent
    private Intent mediaProjectionIntent;
    //投屏服务ipc通道
    public IMediaProjectionServiceIPC projectionServiceIPC;
    private final Logger logger = LoggerFactory.getLogger(ConnectMainService.class);


    public void setMediaProjectionIntent(Intent mediaProjectionIntent) {
        this.mediaProjectionIntent = mediaProjectionIntent;
    }

    public Intent getMediaProjectionServiceIntent() {
        return this.mediaProjectionIntent;
    }

    //传入通讯
    public class MyBinder extends Binder implements IBinder {
        public ConnectMainService getService() {
            return ConnectMainService.this;
        }
    }

    private com.example.linktocomputer.service.NotificationListenerService notificationListenerService;
    //状态 当前是否连接成功
    public boolean isConnected = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        computerId = intent.getStringExtra("id");
        certDownloadPort = intent.getIntExtra("certDownloadPort", 0);
        computerAddress = intent.getStringExtra("address");
        pairToken = intent.getStringExtra("pairToken");
        logger.debug("Received start command");
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if(closeConnectionBroadcastReceiver != null)
            unregisterReceiver(closeConnectionBroadcastReceiver);
        if(bindServiceConnection != null)
            unbindService(bindServiceConnection);
        logger.debug("Network service destroy");
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        closeConnectionBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ConnectMainService.this.disconnect();
            }
        };
        registerReceiver(closeConnectionBroadcastReceiver, new IntentFilter("close_connection"));
        createNotificationChannel();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void connect(String url) {
        //防止重复连接
        if(isConnected) return;
        activityMethods.showConnectingDialog();
        Thread websocketThread = new Thread() {
            @Override
            public void run() {
                //证书输入流
                InputStream certInput;
                SSLContext sslContext;
                TrustManager trustManager;
                if(computerId != null) {
                    //检查证书文件
                    File certDir = new File(getDataDir().getAbsolutePath() + "/files/cert/");
                    certDir.mkdirs();
                    File crtCertFile = new File(getDataDir().getAbsolutePath() + "/files/cert/" + computerId + ".crt");
                    File p12CertFile = new File(getDataDir().getAbsolutePath() + "/files/cert/" + computerId + ".p12");
                    if(!crtCertFile.exists() || !p12CertFile.exists()) {
                        logger.info("Missing cert.Redownload");
                        crtCertFile.delete();
                        p12CertFile.delete();
                        if(certDownloadPort == 0) {
                            logger.warn("Invalid cert download port");
                            activityMethods.showAlert("连接失败", "PC端证书服务异常", "确定");
                            activityMethods.closeConnectingDialog();
                            stopSelf();
                            return;
                        }
                        //下载
                        //使用预制证书加密 好过明文裸奔
                        SSLContext certDownloadSSLContext;
                        TrustManager downloadCertTrustManager;
                        try {
                            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                            keyStore.load(null);
                            String certificateAlias = Integer.toString(0);
                            keyStore.setCertificateEntry(certificateAlias, certificateFactory.generateCertificate(getResources().openRawResource(R.raw.default_cert)));
                            certDownloadSSLContext = SSLContext.getInstance("TLS");
                            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                            trustManagerFactory.init(keyStore);
                            downloadCertTrustManager = trustManagerFactory.getTrustManagers()[0];
                            certDownloadSSLContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                        } catch (CertificateException | IOException | NoSuchAlgorithmException |
                                 KeyStoreException |
                                 KeyManagementException e) {
                            logger.error("Failed to load default cert to download computer cert", e);
                            activityMethods.showAlert("连接失败", "SSL证书读取异常:" + e, "确定");
                            activityMethods.closeConnectingDialog();
                            stopSelf();
                            return;
                        }
                        logger.debug("Create cert download request");
                        Request certRequest = new Request.Builder()
                                .url("https://" + computerAddress + ":" + certDownloadPort)
                                .removeHeader("User-Agent")
                                .addHeader("User-Agent", "I HATE YOU")
                                .addHeader("suisho-pair-token", pairToken)
                                .removeHeader("Accept-Encoding")
                                .addHeader("Accept-Encoding", "identity")
                                .method("GET", null)
                                .build();
                        //构建client下载
                        OkHttpClient downloadClient = new OkHttpClient.Builder()
                                .sslSocketFactory(certDownloadSSLContext.getSocketFactory(), (X509TrustManager) downloadCertTrustManager)
                                .hostnameVerifier((hostname, session) -> hostname.equals(computerAddress))
                                .writeTimeout(Duration.ofSeconds(10))
                                .callTimeout(Duration.ofSeconds(10))
                                .build();
                        try (Response response = downloadClient.newCall(certRequest).execute()) {
                            if(response.code() != 200) {
                                logger.warn("Failed to download cert with code:{}", response.code());
                                activityMethods.showAlert("连接失败", response.code() == 403 ? "下载证书异常:鉴权失败" : "下载证书异常:服务端返回异常", "确定");
                                activityMethods.closeConnectingDialog();
                                stopSelf();
                                return;
                            }
                            byte[] fileData = response.body().bytes();
                            if(fileData.length == 0) {
                                logger.warn("Downloaded empty cert data");
                                activityMethods.showAlert("连接失败", "加载证书异常:PC端发送了空数据", "确定");
                                activityMethods.closeConnectingDialog();
                                stopSelf();
                                return;
                            }
                            File certPackFile = new File(getDataDir().getAbsolutePath() + "/files/cert/" + computerId + ".pak");
                            logger.debug("Cert pack size:{}", certPackFile.length());
                            FileOutputStream certPackOutput = new FileOutputStream(certPackFile);
                            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(certPackOutput);
                            bufferedOutputStream.write(fileData);
                            bufferedOutputStream.flush();
                            bufferedOutputStream.close();
                            certPackOutput.flush();
                            certPackOutput.close();
                            //释放证书
                            logger.debug("Extract cert file");
                            FileInputStream certPackInputStream = new FileInputStream(certPackFile);
                            byte[] crtFileLengthByte = new byte[2];
                            certPackInputStream.read(crtFileLengthByte, 0, 2);
                            ByteBuffer wrapedByteBuffer = ByteBuffer.wrap(crtFileLengthByte, 0, 2);
                            short crtFileSize = wrapedByteBuffer.getShort();
                            logger.debug(".crt file size:{}", crtFileSize);
                            byte[] crtFileBuffer = new byte[crtFileSize];
                            byte[] p12FileBuffer = new byte[(int) (certPackFile.length() - 2 - crtFileSize)];
                            //释放.crt
                            certPackInputStream.read(crtFileBuffer);
                            FileOutputStream crtFileOutputStream = new FileOutputStream(crtCertFile);
                            crtFileOutputStream.write(crtFileBuffer);
                            crtFileOutputStream.flush();
                            crtFileOutputStream.close();
                            //释放.p12
                            certPackInputStream.read(p12FileBuffer);
                            FileOutputStream p12FileOutputStream = new FileOutputStream(p12CertFile);
                            p12FileOutputStream.write(p12FileBuffer);
                            p12FileOutputStream.flush();
                            p12FileOutputStream.close();
                            //扫尾
                            certPackInputStream.close();
                            certPackFile.delete();
                            logger.debug("Success download and extract cert file");
                        } catch (IOException | NullPointerException e) {
                            logger.error("Error on download or extract certs", e);
                            activityMethods.showAlert("连接失败", "下载证书异常", "确定");
                            activityMethods.closeConnectingDialog();
                            stopSelf();
                            return;
                        }
                    }
                    try {
                        certInput = Files.newInputStream(crtCertFile.toPath());
                    } catch (IOException e) {
                        logger.error("Error on load cert file", e);
                        activityMethods.showAlert("连接失败", "加载证书文件异常", "确定");
                        activityMethods.closeConnectingDialog();
                        stopSelf();
                        return;
                    }
                } else {
                    logger.info("Using default cert file to connect");
                    certInput = getResources().openRawResource(R.raw.default_cert);
                }
                //websocket连接 证书读取
                try {
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keyStore.load(null);
                    String certificateAlias = Integer.toString(0);
                    keyStore.setCertificateEntry(certificateAlias, certificateFactory.generateCertificate(certInput));
                    sslContext = SSLContext.getInstance("TLS");
                    final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(keyStore);
                    trustManager = trustManagerFactory.getTrustManagers()[0];
                    sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                } catch (CertificateException | IOException | NoSuchAlgorithmException |
                         KeyStoreException |
                         KeyManagementException e) {
                    logger.error("Error on load cert file for main connection", e);
                    activityMethods.showAlert("连接失败", "SSL证书读取异常:" + e, "确定");
                    activityMethods.closeConnectingDialog();
                    stopSelf();
                    return;
                }
                logger.debug("Ready main connection");
                Request wsReq = new Request.Builder()
                        .url(url)
                        .addHeader("suisho-pair-token", pairToken)
                        .build();
                webSocketClient = new OkHttpClient()
                        .newBuilder()
                        .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManager)
                        .hostnameVerifier((hostname, session) -> hostname.equals(computerAddress))
                        .writeTimeout(Duration.ofSeconds(10))
                        .callTimeout(Duration.ofSeconds(10))
                        .build()
                        .newWebSocket(wsReq, new WebSocketListener() {
                            @Override
                            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                                super.onOpen(webSocket, response);
                                //握手
                                logger.debug("Send handshake packet");
                                webSocketClient.send(buildHandshakeJson());
                            }

                            @Override
                            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                                super.onClosed(webSocket, code, reason);
                                //关闭音频
                                logger.info("Connection close with code:{}", code);
                                if(projectionServiceIPC != null) {
                                    try {
                                        logger.debug("Closing media projection service");
                                        projectionServiceIPC.exit();
                                    } catch (RemoteException ignored) {
                                    } finally {
                                        ConnectMainService.this.projectionServiceIPC = null;
                                        ConnectMainService.this.mediaProjectionIntent = null;
                                    }
                                }
                                if(webFileServer != null) webFileServer.stop();
                                isConnected = false;
                                NewMainActivity activity = activityMethods.getActivity();
                                if(!activity.isDestroyed()) {
                                    activity.unregisterNetworkCallback();
                                }
                                if(GlobalVariables.preferences.getBoolean("function_auto_exit_on_disconnect", false)) {
                                    logger.debug("User enabled auto exit,check activity on top");
                                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                                    am.getRunningAppProcesses().forEach(runningAppProcessInfo -> {
                                        if(runningAppProcessInfo.processName.equals("com.suisho.linktocomputer")) {
                                            if(runningAppProcessInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && runningAppProcessInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING) {
                                                //直接killProcess退出会导致下次启动带上莫名其妙的savedInstanceState
                                                logger.info("Activity not on stack top.Suicide");
                                                activityMethods.getActivity().finishAffinity();
                                                System.exit(0);
                                            } else {
                                                if(code != ConnectionCloseCode.CloseFromClient) {
                                                    //检测是否在栈顶
                                                    am.getAppTasks().forEach(appTask -> {
                                                        if(appTask.getTaskInfo().baseIntent.getComponent().getShortClassName().equals(NewMainActivity.class.getName())) {
                                                            logger.info("Activity on stack top,show disconnect alert");
                                                            activityMethods.showAlert("通讯关闭", reason.isEmpty() ? "计算机关闭连接" : reason, "确定");
                                                            activityMethods.closeConnectingDialog();
                                                        }
                                                    });
                                                }
                                                stopSelf();
                                            }
                                        }
                                    });
                                }
                                stopSelf();
                            }

                            @Override
                            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                                super.onFailure(webSocket, t, response);
                                logger.warn("Main connection failure called", t);
                                isConnected = false;
                                activityMethods.closeConnectingDialog();
                                activityMethods.onDisconnect();
                                if(batteryStateReceiver != null)
                                    try {
                                        //偶发异常
                                        unregisterReceiver(batteryStateReceiver);
                                    } catch (IllegalArgumentException ignore) {
                                    }
                                ;
                                if(webFileServer != null) webFileServer.stop();
                                try {
                                    if(Objects.requireNonNull(t.getMessage()).contains("java.security.cert.CertPathValidatorException")) {
                                        //证书异常
                                        logger.warn("Disconnect because SSL verify failed");
                                        activityMethods.showAlert(R.string.text_error, R.string.text_ssl_verify_error, R.string.text_ok);
                                    } else if(Objects.requireNonNull(t.getMessage()).contains("Connection reset")) {
                                        logger.warn("Disconnect because connection reset");
                                        activityMethods.showAlert("通讯关闭", "连接异常中断", "确定");
                                        this.onClosed(webSocket, 1000, "");
                                    } else {
                                        logger.warn("Disconnect because unknown error");
                                        activityMethods.showAlert(R.string.text_error, R.string.dialog_connectFailedAlertText, R.string.text_ok);
                                    }
                                } catch (NullPointerException nullPointerException) {
                                    logger.warn("Disconnect because unknown reason null pointer", nullPointerException);
                                    activityMethods.showAlert(R.string.text_error, R.string.dialog_connectFailedAlertText, R.string.text_ok);
                                    stopSelf();
                                }
                                if(projectionServiceIPC != null) {
                                    try {
                                        projectionServiceIPC.exit();
                                    } catch (RemoteException ignored) {
                                    }
                                }
                                stopSelf();
                            }

                            @Override
                            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                                super.onMessage(webSocket, text);
                                try {
                                    MainServiceJson jsonObj = GlobalVariables.jsonBuilder.fromJson(text, MainServiceJson.class);
                                    if(!isHandShaken && !jsonObj.packetType.equals("connect_ping")) {
                                        logger.warn("Connection first packet not handshake,disconnect");
                                        webSocketClient.close(ConnectionCloseCode.CloseFromClientError, "服务端异常:未完成握手");
                                        return;
                                    }
                                    if(jsonObj._responseId != null) {
                                        /*交给一个方法处理*/
                                        logger.debug("Response packet received,id:{}", jsonObj._responseId);
                                        onResponsePacket(jsonObj._responseId, text);
                                        return;
                                    }
                                    switch (jsonObj.packetType) {
                                        case "connect_ping":
                                            //发起连接返回包
                                            isHandShaken = true;
                                            GlobalVariables.computerName = jsonObj.name;
                                            //此时msg发送的是设备id
                                            GlobalVariables.computerId = jsonObj.msg;
                                            HandshakeResponse handshakeResponse = new HandshakeResponse(ConnectMainService.this);
                                            logger.debug("Send handshake response packet");
                                            webSocketClient.send(handshakeResponse.build().toString());
                                            break;
                                        case "connect_success":
                                            //连接成功事件
                                            GlobalVariables.serverAddress = jsonObj.msg;
                                            isConnected = true;
                                            activityMethods.closeConnectingDialog();
                                            Snackbar.make(activityMethods.getActivity().findViewById(R.id.homeViewPager2), R.string.service_connect_success, 2000).show();
                                            activityMethods.onConnected(jsonObj.sessionId);
                                            certInput.close();
                                            logger.info("Connection handshake success!");
                                            File p12CertFile = new File(getDataDir().getAbsolutePath() + "/files/cert/" + computerId + ".p12");
                                            webFileServer.init(Files.newInputStream(p12CertFile.toPath()), jsonObj.sessionId, activityMethods.getActivity());
                                            logger.info("Start file manager init");
                                            break;
                                        case "main_server_initialled":
                                            //pc端窗口初始化完成
                                            //注册电池状态广播
                                            IntentFilter batteryBroadcastFilter = new IntentFilter();
                                            batteryBroadcastFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
                                            if(batteryStateReceiver == null) {
                                                logger.debug("Register battery state receiver");
                                                batteryStateReceiver = new BatteryStateReceiver(ConnectMainService.this);
                                            }
                                            registerReceiver(batteryStateReceiver, batteryBroadcastFilter);
                                            setupNotificationListenerService();
                                            break;
                                        case "main_getDeviceDetailInfo":
                                            //获取详细信息
                                            //详细信息构建模块
                                            DetailBuilder detailResponse = new DetailBuilder(jsonObj._request_id, getApplicationContext());
                                            //返回详细信息
                                            webSocketClient.send(detailResponse.create().toString());
                                            break;
                                        case "transmit_text":
                                            //互传文本
                                            logger.debug("Receive transmit text");
                                            activityMethods.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_TEXT, new TransmitMessageTypeText(jsonObj.msg, false), true);
                                            break;
                                        case "transmit_uploadFile":
                                            //互传文件
                                            File file;
                                            logger.debug("Transmit request download file");
                                            //Download目录下
                                            if(GlobalVariables.preferences.getInt("file_save_location", 0) == 1) {
                                                logger.debug("Save to public download path");
                                                file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit");
                                            } else {
                                                //私有目录
                                                logger.debug("Save to private path");
                                                file = new File(ConnectMainService.this.getExternalFilesDir(null).getAbsolutePath() + "/transmit/files");
                                            }
                                            file.mkdirs();
                                            File outputFile = new File(file.getAbsolutePath() + "/" + jsonObj.fileName);
                                            logger.debug("Create output file: {}", outputFile.getAbsolutePath());
                                            if(outputFile.exists()) {
                                                //重名 末尾加时间戳保存
                                                //不能影响显示
                                                logger.debug("File exists, append timestamp to new file name");
                                                new TransmitDownloadFile(jsonObj.port, file.getAbsolutePath() + "/" + System.currentTimeMillis() + jsonObj.fileName, jsonObj.fileName, jsonObj.fileSize, activityMethods);
                                            } else {
                                                //没重名 一切正常
                                                new TransmitDownloadFile(jsonObj.port, outputFile.getAbsolutePath(), jsonObj.fileName, jsonObj.fileSize, activityMethods);
                                            }
                                            webSocketClient.send(EmptyResponsePacketBuilder.buildEmptyResponsePacket(jsonObj).toString());
                                            break;
                                        case "main_queryAllPackages":
                                            //查询所有已安装应用
                                            logger.debug("Received query all packages packet");
                                            new Thread(() -> {
                                                JsonObject responseObj = new AllPackageResponse(jsonObj._request_id, ConnectMainService.this).create();
                                                webSocketClient.send(responseObj.toString());
                                            }).start();
                                            break;
                                        case "main_bindDevice":
                                            //绑定计算机
                                            logger.debug("Received bind device packet");
                                            new Thread(() -> {
                                                File keyFile = new File(getFilesDir() + "/bind.key");
                                                if(keyFile.exists()) {
                                                    logger.debug("Has exists bind key file.Delete it");
                                                    keyFile.delete();
                                                }
                                                JsonObject response = new JsonObject();
                                                response.addProperty("_isResponsePacket", true);
                                                response.addProperty("_responseId", jsonObj._request_id);
                                                try (FileOutputStream keyFileOut = new FileOutputStream(keyFile)) {
                                                    keyFileOut.write(jsonObj.msg.getBytes());
                                                    keyFileOut.flush();
                                                    GlobalVariables.settings.edit().putBoolean("boundDevice", true).apply();
                                                    response.addProperty("success", true);
                                                    logger.info("Bind computer success");
                                                } catch (IOException e) {
                                                    logger.error("Error when write bind key file", e);
                                                    response.addProperty("success", false);
                                                } finally {
                                                    webSocketClient.send(response.toString());
                                                }
                                            }).start();
                                            break;
                                        case "main_unbindDevice":
                                            //解绑计算机
                                            File keyFile = new File(getFilesDir() + "/bind.key");
                                            keyFile.delete();
                                            GlobalVariables.settings.edit().putBoolean("boundDevice", false).apply();
                                            JsonObject response = new JsonObject();
                                            response.addProperty("_isResponsePacket", true);
                                            response.addProperty("_responseId", jsonObj._request_id);
                                            logger.info("Unbind computer success");
                                            webSocketClient.send(response.toString());
                                            break;
                                        case "main_getTrustMode":
                                            JsonObject trustModePacket = new JsonObject();
                                            trustModePacket.addProperty("_isResponsePacket", true);
                                            trustModePacket.addProperty("_responseId", jsonObj._request_id);
                                            trustModePacket.addProperty("trustMode", GlobalVariables.computerConfigManager.isTrustedComputer() ? ComputerTrustMode.TRUST_TRUSTED : ComputerTrustMode.TRUST_UNTRUSTED);
                                            webSocketClient.send(trustModePacket.toString());
                                            break;
                                        case "main_hasMediaProjectionPermission":
                                            //检查投屏权限
                                            JsonObject mediaProjectionPermissionQueryPacket = new JsonObject();
                                            mediaProjectionPermissionQueryPacket.addProperty("_isResponsePacket", true);
                                            mediaProjectionPermissionQueryPacket.addProperty("_responseId", jsonObj._request_id);
                                            mediaProjectionPermissionQueryPacket.addProperty("hasPermission", mediaProjectionIntent != null);
                                            webSocketClient.send(mediaProjectionPermissionQueryPacket.toString());
                                            break;
                                        case "main_startAudioForward":
                                            JsonObject mediaProjectionPacket = new JsonObject();
                                            mediaProjectionPacket.addProperty("_isResponsePacket", true);
                                            mediaProjectionPacket.addProperty("_responseId", jsonObj._request_id);
                                            //是否已经授权
                                            //TODO 支持使用shell或root授权
                                            if(mediaProjectionIntent == null) {
                                                logger.debug("Request media projection but not permission");
                                                mediaProjectionPacket.addProperty("result", false);
                                                webSocketClient.send(mediaProjectionPacket.toString());
                                                return;
                                            }
                                            mediaProjectionPacket.addProperty("result", true);
                                            Intent intent = new Intent(ConnectMainService.this, MediaProjectionService.class);
                                            logger.info("Start audio forward service");
                                            bindServiceConnection = new ServiceConnection() {
                                                @Override
                                                public void onServiceConnected(ComponentName name, IBinder service) {
                                                    projectionServiceIPC = IMediaProjectionServiceIPC.Stub.asInterface(service);
                                                    try {
                                                        projectionServiceIPC.setTargetAddress(computerAddress);
                                                        projectionServiceIPC.setScreenIntent(mediaProjectionIntent);
                                                        projectionServiceIPC.setEncryptData(jsonObj.key, jsonObj.iv);
                                                        projectionServiceIPC.run();
                                                    } catch (RemoteException e) {
                                                        logger.error("Error when start audio forward service", e);
                                                        mediaProjectionPacket.addProperty("result", false);
                                                        mediaProjectionPacket.addProperty("exception", true);
                                                        webSocketClient.send(mediaProjectionPacket.toString());
                                                    }
                                                }

                                                @Override
                                                public void onServiceDisconnected(ComponentName name) {
                                                }
                                            };
                                            bindService(intent, bindServiceConnection, BIND_AUTO_CREATE);
                                            webSocketClient.send(mediaProjectionPacket.toString());
                                            break;
                                        case "main_stopAudioForward":
                                            JsonObject stopProjectionPacket = new JsonObject();
                                            stopProjectionPacket.addProperty("_isResponsePacket", true);
                                            stopProjectionPacket.addProperty("_responseId", jsonObj._request_id);
                                            logger.info("Stop audio forward service");
                                            if(projectionServiceIPC != null) {
                                                try {
                                                    projectionServiceIPC.exit();
                                                } catch (RemoteException ignored) {
                                                } finally {
                                                    mediaProjectionIntent = null;
                                                    projectionServiceIPC = null;
                                                    unbindService(bindServiceConnection);
                                                }
                                            }
                                            webSocketClient.send(stopProjectionPacket.toString());
                                            activityMethods.getActivity().updateConnectionStateDisplay();
                                            break;
                                        case "main_checkPermission":
                                            JsonObject permissionCheckPacket = new JsonObject();
                                            permissionCheckPacket.addProperty("_isResponsePacket", true);
                                            permissionCheckPacket.addProperty("_responseId", jsonObj._request_id);
                                            //存储空间权限
                                            if(jsonObj.name.equals("android.permission.MANAGE_EXTERNAL_STORAGE")) {
                                                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                                                    permissionCheckPacket.addProperty("result", Environment.isExternalStorageManager());
                                                } else {
                                                    boolean hasPermission = checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
                                                    permissionCheckPacket.addProperty("result", hasPermission);
                                                }
                                                webSocketClient.send(permissionCheckPacket.toString());
                                                return;
                                            }
                                            webSocketClient.send(permissionCheckPacket.toString());
                                            break;
                                        case "file_getFilesList":
                                            JsonObject fileListPacket = new JsonObject();
                                            fileListPacket.addProperty("_isResponsePacket", true);
                                            fileListPacket.addProperty("_responseId", jsonObj._request_id);
                                            //功能是否开启及设备是否被信任
                                            if(!GlobalVariables.preferences.getBoolean("function_file_manager", false) || !GlobalVariables.computerConfigManager.isTrustedComputer()) {
                                                fileListPacket.addProperty("code", GlobalVariables.computerConfigManager.isTrustedComputer() ? FileManagerResultCode.CODE_FUNCTION_DISABLED : FileManagerResultCode.CODE_DEVICE_NOT_TRUSTED);
                                                webSocketClient.send(fileListPacket.toString());
                                                return;
                                            }
                                            //读文件
                                            File dir = new File(jsonObj.msg);
                                            if(!dir.isDirectory()) {
                                                //不是目录
                                                logger.debug("Access path '{}' not directory", dir.getPath());
                                                fileListPacket.addProperty("code", FileManagerResultCode.CODE_NOT_DIR);
                                                webSocketClient.send(fileListPacket.toString());
                                                return;
                                            }
                                            File[] files = dir.listFiles();
                                            if(files == null) {
                                                //无法列出文件
                                                logger.debug("Access path '{}' not permission", dir.getPath());
                                                fileListPacket.addProperty("code", FileManagerResultCode.CODE_NOT_PERMISSION);
                                                webSocketClient.send(fileListPacket.toString());
                                                return;
                                            }
                                            JsonArray fileListJsonArray = new JsonArray();
                                            for(File inDirectoryFile : files) {
                                                JsonObject fileInfoObject = new JsonObject();
                                                fileInfoObject.addProperty("type", inDirectoryFile.isDirectory() ? "folder" : "file");
                                                fileInfoObject.addProperty("name", inDirectoryFile.getName());
                                                fileInfoObject.addProperty("size", inDirectoryFile.length());
                                                fileListJsonArray.add(fileInfoObject);
                                            }
                                            fileListPacket.addProperty("code", FileManagerResultCode.CODE_NORMAL);
                                            fileListPacket.add("files", fileListJsonArray);
                                            webSocketClient.send(fileListPacket.toString());
                                            //已经访问了目录 提前开启服务器
                                            logger.debug("Prestart file server for access file");
                                            if(!webFileServer.wasStarted()) webFileServer.start();
                                            break;
                                        case "main_getCurrentNotificationsList":
                                            CurrentNotificationsListPacket packet = new CurrentNotificationsListPacket();
                                            JsonObject notificationListJson = packet.build(jsonObj._request_id, notificationListenerService.getActiveNotifications(), notificationListenerService,checkNotificationListenerPermission());
                                            sendObject(notificationListJson);
                                            break;
                                        case "removeCurrentNotification":
                                            if(jsonObj.key.equals("all")) {
                                                logger.debug("Request remove all active notification");
                                                notificationListenerService.cancelAllNotifications();
                                                break;
                                            }
                                            logger.debug("Request remove notification with key:{}", jsonObj.key);
                                            notificationListenerService.cancelNotification(jsonObj.key);
                                            break;
                                        case "appendMediaSessionControl":
                                            if(notificationListenerService != null) {
                                                notificationListenerService.appendMediaSessionControl(jsonObj.msg, jsonObj.time);
                                            }
                                            break;
                                        default:
                                            logger.warn("Unknown packet type:{}", jsonObj.packetType);
                                            break;
                                    }
                                } catch (JsonSyntaxException e) {
                                    webSocketClient.close(ConnectionCloseCode.CloseFromClientError, "解析数据包失败");
                                    logger.error("Failed to parse json", e);
                                } catch (IOException e) {
                                    //文件浏览模块开启异常 读证书报错
                                    logger.error("Failed to start file server", e);
                                    JsonObject jsonObject = new JsonObject();
                                    jsonObject.addProperty("packetType", "edit_state");
                                    jsonObject.addProperty("type", "add");
                                    jsonObject.addProperty("name", "error_phone_file_server");
                                    sendObject(jsonObject);
                                    activityMethods.getActivity().stateBarManager.addState(States.getStateList().get("error_phone_file_server"));
                                }
                            }

                            @Override
                            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                                super.onClosing(webSocket, code, reason);
                                webSocketClient.close(ConnectionCloseCode.CloseFromClient, null);
                                isConnected = false;
                                activityMethods.onDisconnect();
                                //注销电池广播监听
                                if(batteryStateReceiver != null)
                                    unregisterReceiver(batteryStateReceiver);
                            }
                        });
            }
        };
        websocketThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    //发送字符串给pc端
    @Override
    public void sendString(String data) {
        webSocketClient.send(data);
    }

    public void sendObject(JsonObject object) {
        webSocketClient.send(object.toString());
    }

    public void setActivityMethods(IConnectedActivityMethods activityMethod) {
        activityMethods = activityMethod;
    }

    /**
     * 发送请求包
     *
     * @param object 数据对象
     * @param handle 返回时回调
     */
    @Override
    public void sendRequestPacket(JsonObject object, RequestHandle handle) {
        //打入请求id
        String reqId = UUID.randomUUID().toString().replace("-", "");
        object.addProperty("_requestId", reqId);
        logger.debug("Send request packet with request id:{}", reqId);
        requestMapping.put(reqId, handle);
        //发送
        webSocketClient.send(object.toString());
    }

    public void showForegroundNotification(Activity activity) {
        logger.debug("Show foreground service notification");
        startForeground(127, buildForegroundNotification(activity));
    }

    @Override
    public void uploadFile(InputStream stream, int port, boolean isSmallFile, @Nullable FileUploadStateHandle onErrorListener, long fileSize, EncryptionKey encryptionKey) {
        new TransmitUploadFile(stream, port, isSmallFile, onErrorListener, this, fileSize, encryptionKey);
    }

    @Override
    public void onResponsePacket(String id, String data) {
        //检查存在
        logger.debug("Receive response packet with id:{}", id);
        if(requestMapping.containsKey(id)) {
            RequestHandle handle = requestMapping.get(id);
            //AS报黄了 还是加判断吧 反正没影响
            if(handle != null) {
                handle.run(data);
            }
            requestMapping.remove(id);
        } else {
            logger.warn("Invalid Response Packet ID:{}", id);
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        disconnect(ConnectionCloseCode.CloseFromClient, null);
    }

    public void disconnect(int code, @Nullable String reason) {
        if(webSocketClient != null) {
            new Timer("CloseConnectionWatchdog").schedule(new TimerTask() {
                @Override
                public void run() {
                    if(webSocketClient != null) {
                        logger.debug("Disconnect timeout,Force close connection");
                        webSocketClient.cancel();
                    }
                    ;
                }
            }, 1000L);
            webSocketClient.close(code, reason);
        }
    }

    private String buildHandshakeJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("packetType", "connect_handshake");
        //协议版本
        obj.addProperty("protocolVersion", 1);
        return obj.toString();
    }

    //检查是否有所有通知管理权限
    public boolean checkNotificationListenerPermission() {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(this);
        return packageNames.contains(getApplicationContext().getPackageName());
    }

    public void setupNotificationListenerService() {
        //通知监听服务
        Intent listenerServiceIntent = new Intent(this, NotificationListenerService.class);
        listenerServiceIntent.setAction("networkServiceLaunch");
        logger.debug("Bind notification listener service");
        bindService(listenerServiceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                //获取服务
                com.example.linktocomputer.service.NotificationListenerService.MyBinder notificationServiceBinder = (com.example.linktocomputer.service.NotificationListenerService.MyBinder) service;
                notificationListenerService = notificationServiceBinder.getService();
                //防止系统没绑定导致接收不到
                if(!NotificationListenerService.systemBound) {
                    logger.info("System not bind notification listener service.Request rebind");
                    PackageManager pm = getPackageManager();
                    pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                    pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    NotificationListenerService.requestRebind(name);
                }
                //把自己传进去 免得啰里啰唆一大堆
                notificationListenerService.setMainService(ConnectMainService.this);
                notificationListenerService.setEnable(GlobalVariables.preferences.getBoolean("function_notification_forward", false));
                notificationListenerServiceWorking = true;
                activityMethods.onNotificationListenerServiceConnect();
                logger.debug("Notification listener service bound");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                notificationListenerServiceWorking = false;
            }
        }, BIND_AUTO_CREATE);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private Notification buildForegroundNotification(Activity activity) {
        Notification.Builder nBuilder = new Notification.Builder(this.getApplicationContext(), "MainServiceNotification");
        Intent notificationBodyIntent = new Intent(activity, activity.getClass());
        notificationBodyIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent notificationBodyPendingIntent = PendingIntent.getActivity(activity, 126, notificationBodyIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent notificationButtonIntent = new Intent("close_connection");
        PendingIntent notificationButtonPendingIntent = PendingIntent.getBroadcast(this, 127, notificationButtonIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        nBuilder.setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle(getText(R.string.service_notification_title))
                .setContentText(getText(R.string.service_notification_content))
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(R.drawable.baseline_close_24, getText(R.string.service_notification_button_close), notificationButtonPendingIntent)
                .setContentIntent(notificationBodyPendingIntent)
                .setChannelId("foregroundService");
        logger.debug("Created foreground service notification");
        return nBuilder.build();
    }

    //创建通知渠道 重复创建不会有任何问题
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel("foregroundService", "前台服务通知", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("请勿关闭该通知");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        logger.debug("Created foreground notification channel");
    }

    public NotificationListenerService getNotificationListenerService() {
        return notificationListenerService;
    }

    public boolean getNotificationListenerWorking() {
        return notificationListenerServiceWorking;
    }
}