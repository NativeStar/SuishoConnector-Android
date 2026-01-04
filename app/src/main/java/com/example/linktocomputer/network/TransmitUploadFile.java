package com.example.linktocomputer.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.abstracts.FileUploadStateHandle;
import com.example.linktocomputer.constant.NotificationID;
import com.example.linktocomputer.instances.EncryptionKey;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TransmitUploadFile {
    public static boolean hasUploadingFile=false;
    //源文件输入 读取用户选择的文件
    private final InputStream fileInputStream;
    //socket输出 上行文件数据用
    private OutputStream socketOutputStream;
    private Socket socket;
    private final int serverPort;
    private final int maxBufferSize;
    private FileUploadStateHandle stateHandle;
    private final Context serviceContext;
    //通知对象
    private Notification.Builder notificationBuilder;
    private long uploadedSize=0;
    private final boolean enableNotification;
    private final long fileSize;
    private Cipher cipher;
    /**
     * 节流 更新状态栏进度很浪费性能
     */
    private byte progressUpdateCount=0;
    public TransmitUploadFile(InputStream stream, int port, boolean isSmallFile, FileUploadStateHandle handle, Context context, long fileSize, EncryptionKey encryptionKey) {
        fileInputStream = stream;
        serverPort = port;
        this.fileSize=fileSize;
        serviceContext=context;
        //设置最小缓冲区
        if(isSmallFile){
            maxBufferSize=32;
        }else {
            maxBufferSize=1024*512;
        }
        if (handle != null) {
            stateHandle = handle;
        }
        enableNotification=ensureNotification();
        //初始化加密
        SecretKeySpec secretKey=new SecretKeySpec(encryptionKey.getKeyEncoded(),"AES");
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            IvParameterSpec ivParameterSpec=new IvParameterSpec(encryptionKey.getIv());
            cipher.init(Cipher.ENCRYPT_MODE,secretKey,ivParameterSpec);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            stateHandle.onError(e);
            return;
        }
        start();
    }
    void start() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                super.run();
                hasUploadingFile=true;
                try {
                    socket = new Socket(GlobalVariables.serverAddress, serverPort);
                    //保活
                    socket.setKeepAlive(true);
                    socketOutputStream = socket.getOutputStream();
                    InputStream socketInputStream = socket.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socketInputStream));
                    socketOutputStream.write(GlobalVariables.computerConfigManager.getSessionId().getBytes());
                    String msg = bufferedReader.readLine();
                    if (msg != null) {
                        if (!msg.equals("START")) {
                            stateHandle.onError(new IOException("PC端拒绝接收请求"));
                            return;
                        }
                    } else {
                        stateHandle.onError(new IOException("PC端响应无效"));
                        if (socket.isConnected()) {
                            socket.close();
                        }
                    }
                    //开始上传回调
                    stateHandle.onStart();
                    //正式上传数据
                    BufferedInputStream bufferedFileInputStream=new BufferedInputStream(fileInputStream);
                    while (true) {
//                        int availLength = Math.min(bufferedFileInputStream.available(), maxBufferSize);
                        //修复大文件无法传输
                        //获取剩余未传输文件大小 避免末尾多余空数据
                        int availLength = (int) Math.min(maxBufferSize,Math.abs(fileSize-uploadedSize));
                        byte[] buffer = new byte[availLength];
                        if (bufferedFileInputStream.read(buffer) <= -1||Math.abs(availLength)==0) {
                            byte[] encryptedBuffer= cipher.doFinal(buffer);
                            socketOutputStream.write(encryptedBuffer);
                            //看能不能解决漏数据
                            sleep(300);
                            socketOutputStream.close();
                            break;
                        }
                        byte[] encryptedBuffer= cipher.update(buffer);
                        socketOutputStream.write(encryptedBuffer);
                        uploadedSize+=buffer.length;
                        //没权限直接跳过
                        if(!enableNotification) continue;
                        //性能优化 只有更新达到次数才执行回调
                        if (progressUpdateCount >= 16){
                            stateHandle.onProgress(uploadedSize,notificationBuilder);
                            progressUpdateCount=0;
                        }else{
                            progressUpdateCount++;
                        }
                    }
                    try {
                        sleep(700);
                        fileInputStream.close();
                        bufferedReader.close();
                        socketInputStream.close();
                        socket.close();
                        bufferedFileInputStream.close();
                        hasUploadingFile=false;
                        //上传完成回调
                        stateHandle.onSuccess();
                    } catch (InterruptedException ie) {
                        hasUploadingFile=false;
                    }
                } catch (IOException | BadPaddingException | IllegalBlockSizeException e) {
                    if (stateHandle == null) {
                        Log.e("main", e.getMessage());
                        hasUploadingFile=false;
                        return;
                    }
                    stateHandle.onError(e);
                } catch (InterruptedException ie) {
                    hasUploadingFile=false;
                }
            }
        };
        thread.start();
    }

    /**
     * 检查是否有权限并创建通知
     */
    private boolean ensureNotification(){
        NotificationManager notificationManager=serviceContext.getSystemService(NotificationManager.class);
        if (!Util.checkNotificationPermission(notificationManager)) return false;
        createNotificationChannel(notificationManager);
        Notification.Builder builder=new Notification.Builder(serviceContext,"fileUploadProgress");
        builder.setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle("文件上传中...")
                .setContentText("请等待上传完成")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setProgress(100,0,false);
        notificationBuilder=builder;
        notificationManager.notify(NotificationID.NOTIFICATION_TRANSMIT_UPLOAD_FILE, notificationBuilder.build());
        return true;
    }
    private void createNotificationChannel(NotificationManager notificationManager){
        //判断通道是否存在
        if (notificationManager.getNotificationChannel("fileUploadProgress")!= null) {
            return;
        }
        NotificationChannel channel=new NotificationChannel("fileUploadProgress","文件上传进度显示", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("请勿关闭该通知");
        notificationManager.createNotificationChannel(channel);
    }
}
