package com.suisho.linktocomputer.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import com.suisho.linktocomputer.Crystal;
import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.R;
import com.suisho.linktocomputer.Util;
import com.suisho.linktocomputer.abstracts.FileUploadStateHandle;
import com.suisho.linktocomputer.abstracts.RequestHandle;
import com.suisho.linktocomputer.constant.NotificationID;
import com.suisho.linktocomputer.database.TransmitDatabaseEntity;
import com.suisho.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.suisho.linktocomputer.instances.ComputerConfigManager;
import com.suisho.linktocomputer.instances.EncryptionKey;
import com.suisho.linktocomputer.instances.TransmitQueueItem;
import com.suisho.linktocomputer.instances.transmit.TransmitMessageTypeFile;
import com.suisho.linktocomputer.jsonClass.MainServiceJson;
import com.suisho.linktocomputer.jsonClass.TransmitMessage;
import com.suisho.linktocomputer.service.ConnectMainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.ArrayDeque;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.objectbox.Box;

public class TransmitUploadFile {
    public static boolean hasUploadingFile = false;
    private static final ArrayDeque<TransmitQueueItem> uploadFileQueue = new ArrayDeque<>(10);
    //源文件输入 读取用户选择的文件
    private final InputStream fileInputStream;
    //socket输出 上行文件数据用
    private OutputStream socketOutputStream;
    private Socket socket;
    private final int serverPort;
    private final int maxBufferSize;
    private FileUploadStateHandle stateHandle;
    private final Context applicationContext;
    //通知对象
    private Notification.Builder notificationBuilder;
    private long uploadedSize = 0;
    private final boolean enableNotification;
    private final long fileSize;
    private Cipher cipher;
    private static final Logger logger = LoggerFactory.getLogger(TransmitUploadFile.class);

    /**
     * 节流 更新状态栏进度很浪费性能
     */
    private byte progressUpdateCount = 0;

    public TransmitUploadFile(InputStream stream, int port, boolean isSmallFile, FileUploadStateHandle handle, Context context, long fileSize, EncryptionKey encryptionKey) {
        fileInputStream = stream;
        serverPort = port;
        this.fileSize = fileSize;
        applicationContext = context;
        logger.debug("Transmit upload small file:{}", isSmallFile);
        //设置最小缓冲区
        if(isSmallFile) {
            maxBufferSize = 32;
        } else {
            maxBufferSize = 1024 * 512;
        }
        if(handle != null) {
            stateHandle = handle;
        }
        enableNotification = ensureNotification();
        //初始化加密
        SecretKeySpec secretKey = new SecretKeySpec(encryptionKey.getKeyEncoded(), "AES");
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(encryptionKey.getIv());
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            logger.error("Failed to init transmit upload crypt", e);
            stateHandle.onError(e);
            closeStream(stream);
            updateUploadQueue((Crystal) applicationContext);
            return;
        }
        start();
    }

    void start() {
        logger.info("Start upload file");
        Thread thread = new Thread() {
            @Override
            public void run() {
                super.run();
                hasUploadingFile = true;
                InputStream socketInputStream = null;
                BufferedReader bufferedReader = null;
                try {
                    socket = new Socket(GlobalVariables.serverAddress, serverPort);
                    //保活
                    socket.setKeepAlive(true);
                    socketOutputStream = socket.getOutputStream();
                    socketInputStream = socket.getInputStream();
                    bufferedReader = new BufferedReader(new InputStreamReader(socketInputStream));
                    socketOutputStream.write(GlobalVariables.computerConfigManager.getSessionId().getBytes());
                    String msg = bufferedReader.readLine();
                    if(msg != null) {
                        if(!msg.equals("START")) {
                            logger.warn("Computer rejected request");
                            stateHandle.onError(new IOException("PC端拒绝接收请求"));
                            hasUploadingFile = false;
                            return;
                        }
                    } else {
                        stateHandle.onError(new IOException("PC端响应无效"));
                        logger.warn("Computer response invalid");
                        hasUploadingFile = false;
                        if(socket.isConnected()) {
                            socket.close();
                        }
                        return;
                    }
                    //开始上传回调
                    stateHandle.onStart();
                    //正式上传数据
                    BufferedInputStream bufferedFileInputStream = new BufferedInputStream(fileInputStream);
                    while (true) {
                        //修复大文件无法传输
                        //获取剩余未传输文件大小 避免末尾多余空数据
                        int availLength = (int) Math.min(maxBufferSize, Math.abs(fileSize - uploadedSize));
                        byte[] buffer = new byte[availLength];
                        if(bufferedFileInputStream.read(buffer) <= -1 || Math.abs(availLength) == 0) {
                            byte[] encryptedBuffer = cipher.doFinal(buffer);
                            socketOutputStream.write(encryptedBuffer);
                            //看能不能解决漏数据
                            sleep(300);
                            socketOutputStream.close();
                            break;
                        }
                        byte[] encryptedBuffer = cipher.update(buffer);
                        socketOutputStream.write(encryptedBuffer);
                        uploadedSize += buffer.length;
                        //没权限直接跳过
                        if(!enableNotification) continue;
                        //性能优化 只有更新达到次数才执行回调
                        if(progressUpdateCount >= 16) {
                            stateHandle.onProgress(uploadedSize, notificationBuilder);
                            progressUpdateCount = 0;
                        } else {
                            progressUpdateCount++;
                        }
                    }
                    try {
                        sleep(700);
                        socket.close();
                        bufferedFileInputStream.close();
                        hasUploadingFile = false;
                        //上传完成回调
                        logger.info("Transmit upload file success");
                        stateHandle.onSuccess();
                    } catch (InterruptedException ie) {
                        logger.error("Transmit upload end sleep exception", ie);
                        hasUploadingFile = false;
                    }
                } catch (IOException | BadPaddingException | IllegalBlockSizeException e) {
                    if(stateHandle == null) {
                        logger.error("Transmit upload file error", e);
                        return;
                    }
                    stateHandle.onError(e);
                } catch (InterruptedException ie) {
                    logger.error("Transmit upload write end sleep error", ie);
                } finally {
                    hasUploadingFile = false;
                    try {
                        fileInputStream.close();
                        bufferedReader.close();
                        socketInputStream.close();
                    } catch (IOException | NullPointerException e) {
                        logger.error("Transmit upload close stream error", e);
                    }
                    updateUploadQueue((Crystal) applicationContext);
                }
            }
        };
        thread.start();
    }

    /**
     * 检查是否有权限并创建通知
     */
    private boolean ensureNotification() {
        NotificationManager notificationManager = applicationContext.getSystemService(NotificationManager.class);
        if(!Util.checkNotificationPermission(notificationManager)) return false;
        createNotificationChannel(notificationManager);
        Notification.Builder builder = new Notification.Builder(applicationContext, "fileUploadProgress");
        builder.setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle("文件上传中...")
                .setContentText("请等待上传完成")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setProgress(100, 0, false);
        notificationBuilder = builder;
        notificationManager.notify(NotificationID.NOTIFICATION_TRANSMIT_UPLOAD_FILE, notificationBuilder.build());
        logger.debug("Create transmit upload file notification");
        return true;
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        //判断通道是否存在
        if(notificationManager.getNotificationChannel("fileUploadProgress") != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel("fileUploadProgress", "文件上传进度显示", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("请勿关闭该通知");
        notificationManager.createNotificationChannel(channel);
        logger.debug("Create transmit upload file notification channel");
    }

    private static void updateUploadQueue(Crystal context) {
        ComputerConfigManager computerConfigManager = GlobalVariables.computerConfigManager;
        if(computerConfigManager == null) {
//            这种情况通常是传一半掉线了 清空队列避免异常
            clearQueue();
            return;
        }
        ConnectMainService networkService = computerConfigManager.getNetworkService();
        if(networkService == null || !networkService.isConnected) {
            clearQueue();
            return;
        }
        TransmitQueueItem item = uploadFileQueue.pollFirst();
        if(item == null) return;
        networkService.sendRequestPacket(item.requestPacket, new RequestHandle() {
            @Override
            public void run(String data) {
                super.run(data);
                MainServiceJson jsonObj = GlobalVariables.jsonBuilder.fromJson(data, MainServiceJson.class);
                //检查是否发生异常
                try {
                    if(jsonObj._result.equals("ERROR")) {
                        closeStream(item.fileInputStream);
                        updateUploadQueue(context);
                        return;
                    }
                    networkService.uploadFile(item.fileInputStream, jsonObj.port, item.fileSize <= 8192L, new FileUploadStateHandle() {
                        @Override
                        public void onSuccess() {
                            super.onSuccess();
                            NotificationManager notificationService = networkService.getSystemService(NotificationManager.class);
                            notificationService.cancel(NotificationID.NOTIFICATION_TRANSMIT_UPLOAD_FILE);
                            TransmitDatabaseEntity message = new TransmitDatabaseEntity();
                            message.messageFrom = TransmitMessage.MESSAGE_FROM_PHONE;
                            message.type = TransmitMessage.MESSAGE_TYPE_FILE;
                            message.isDeleted = false;
                            message.fileName = item.fileName;
                            message.fileSize = item.fileSize;
                            message.timestamp = System.currentTimeMillis();
                            //上传文件 该属性无效
                            message.filePath = "null";
                            if(networkService.activityMethods != null) {
                                networkService.activityMethods.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_FILE, new TransmitMessageTypeFile(message), true);
                            } else {
                                Box<TransmitDatabaseEntity> box = (context)
                                        .getDatabase().boxFor(TransmitDatabaseEntity.class);
                                box.put(message);
                            }
                            logger.info("Transmit upload queue file success: {}", item.fileName);
                        }
                    }, item.fileSize, item.encryptionKey);
                } catch (Exception e) {
                    logger.error("Transmit upload queue file error", e);
                    closeStream(item.fileInputStream);
                    updateUploadQueue(context);
                }
            }
        });
    }

    private static void closeStream(InputStream itemStream) {
        try {
            itemStream.close();
        } catch (IOException e) {
            logger.warn("Close file input stream error", e);
        }
    }

    public static boolean addQueueItem(TransmitQueueItem item) {
        if(uploadFileQueue.size() < 10) {
            uploadFileQueue.offerLast(item);
            return true;
        }
        closeStream(item.fileInputStream);
        return false;
    }

    public static void clearQueue() {
        uploadFileQueue.forEach(item -> {
            try {
                item.fileInputStream.close();
            } catch (IOException e) {
                logger.warn("Close file input stream error", e);
            }
        });
        uploadFileQueue.clear();
    }
}
