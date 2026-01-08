package com.example.linktocomputer.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.constant.NotificationID;
import com.example.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.example.linktocomputer.instances.transmit.TransmitMessageTypeFile;
import com.example.linktocomputer.interfaces.IConnectedActivityMethods;
import com.example.linktocomputer.jsonClass.TransmitMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class TransmitDownloadFile {
    int socketPort;
    FileOutputStream fileOutputStream;
    WebSocket ws;
    IConnectedActivityMethods activityMethods;
    final boolean hasNotification;
    final NotificationManager notificationManager;
    final String fileName;
    final long fileSize;
    final String filePath;
    SSLContext sslContext;
    TrustManager trustManager;
    public TransmitDownloadFile(int port, String path, String name , long size, IConnectedActivityMethods am) {
        activityMethods=am;
        socketPort = port;
        filePath=path;
        fileName=name;
        fileSize=size;
        notificationManager=am.getActivity().getSystemService(NotificationManager.class);
        hasNotification= Util.checkNotificationPermission(notificationManager);
        start();
    }
    private void start(){
        Thread thread=new Thread(){
            @Override
            public void run() {
                super.run();
                try{
                    fileOutputStream=new FileOutputStream(filePath);
                } catch (IOException e) {
                    onError(e);
                    return;
                }
                File certFile = new File(activityMethods.getActivity().getDataDir().getAbsolutePath() + "/files/cert/" + GlobalVariables.computerConfigManager.getId() + ".crt");
                try (InputStream certFileInputStream=Files.newInputStream(certFile.toPath())){
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keyStore.load(null);
                    String certificateAlias = Integer.toString(0);
                    keyStore.setCertificateEntry(certificateAlias, certificateFactory.generateCertificate(certFileInputStream));
                    sslContext = SSLContext.getInstance("TLS");
                    final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(keyStore);
                    trustManager = trustManagerFactory.getTrustManagers()[0];
                    sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                } catch (IOException | CertificateException | KeyStoreException |
                         NoSuchAlgorithmException | KeyManagementException e) {
                    onError(e);
                }

                Request wsReq=new Request.Builder()
                        .url("wss://"+GlobalVariables.serverAddress+":"+socketPort)
                        .build();
                ws=new OkHttpClient()
                        .newBuilder()
                        .sslSocketFactory(sslContext.getSocketFactory(),(X509TrustManager) trustManager)
                        .hostnameVerifier((hostname, session) -> true)
                        .writeTimeout(Duration.ofSeconds(10))
                        .callTimeout(Duration.ofSeconds(10))
                        .build()
                        .newWebSocket(wsReq, new WebSocketListener() {
                    @Override
                    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                        super.onOpen(webSocket, response);
                        //验证 同时表示已经准备好了
                        if (hasNotification){
                            activityMethods.getActivity().runOnUiThread(TransmitDownloadFile.this::createNotification);
                        }
                        ws.send(GlobalVariables.computerConfigManager.getSessionId());
                    }

                    @Override
                    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
                        super.onMessage(webSocket, bytes);
                        try {
                            //写入文件
                            fileOutputStream.write(bytes.toByteArray());
                        } catch (IOException e) {
                            onError(e);
                            try {
                                fileOutputStream.close();
                            } catch (IOException ignored) {}
                            finally {
                                ws.close(5001,"File written error");
                            }
                        }
                    }
                    @Override
                    public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                        //1000正常 4000验证失败 4001PC端被关闭
                        super.onClosing(webSocket, code, reason);
                        //关闭通知
                        notificationManager.cancel(NotificationID.NOTIFICATION_TRANSMIT_DOWNLOAD_FILE);
                        activityMethods.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_FILE,new TransmitMessageTypeFile(fileName,fileSize, TransmitMessage.MESSAGE_FROM_COMPUTER,false, filePath),true);
                        activityMethods.getTransmitFragment().scrollMessagesViewToBottom(false);
                        try {
                            fileOutputStream.flush();
                            fileOutputStream.close();
                            //失败 删除文件
                            if(code!=1000) {
                                new File(filePath).delete();
                                activityMethods.showAlert("接收文件失败",code==4000?"连接验证失败":"传输提早中断","确定");
                            }
                        } catch (IOException e) {
                            onError(e);
                        }
                        ws.close(1000,null);
                    }

                    @Override
                    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                        super.onFailure(webSocket, t, response);
                        Log.e("main", "error",t);
                    }
                });
            }
        };
        thread.start();
    }
    private void onError(Exception e){
        activityMethods.showAlert("接收失败",e.toString(),"确定");
        //通知pc端
        File file=new File(filePath);
        file.delete();

    }
    private void ensureNotificationChannel(NotificationManager notificationManager){
        //判断通道是否存在
        if (notificationManager.getNotificationChannel("fileDownloadProgress")!= null) {
            return;
        }
        NotificationChannel channel=new NotificationChannel("fileDownloadProgress","文件上传进度显示", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("请勿关闭该通知");
        notificationManager.createNotificationChannel(channel);
    }
    private void createNotification(){
        ensureNotificationChannel(notificationManager);
        Notification.Builder builder=new Notification.Builder(activityMethods.getActivity(),"fileDownloadProgress");
        builder.setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle("文件接收中...")
                .setContentText("请在PC端查看接收进度")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setProgress(100,0,true);
        notificationManager.notify(NotificationID.NOTIFICATION_TRANSMIT_DOWNLOAD_FILE,builder.build());
    }
}
