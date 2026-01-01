package com.example.linktocomputer.activity;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.abstracts.FileUploadStateHandle;
import com.example.linktocomputer.abstracts.RequestHandle;
import com.example.linktocomputer.constant.NotificationID;
import com.example.linktocomputer.database.TransmitDatabaseEntity;
import com.example.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.example.linktocomputer.fragment.TransmitFragment;
import com.example.linktocomputer.instances.EncryptionKey;
import com.example.linktocomputer.instances.transmit.TransmitMessageTypeFile;
import com.example.linktocomputer.instances.transmit.TransmitMessageTypeText;
import com.example.linktocomputer.jsonClass.MainServiceJson;
import com.example.linktocomputer.jsonClass.TransmitMessage;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class FileUploadActivity extends Activity {
    private NotificationManager notificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationManager = getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        //连接判断
        if(GlobalVariables.computerConfigManager == null || !GlobalVariables.computerConfigManager.getNetworkService().isConnected) {
            Toast.makeText(this, R.string.text_need_connect_first, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent intent = getIntent();
        //阻止软件接收来自自身分享的文件
        if(checkFromSelf(intent)) {
            Toast.makeText(this, R.string.text_send_file_from_self, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if(intent.getAction().equals(Intent.ACTION_SEND)) {
            if(intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                confirmSendText(intent.getStringExtra(Intent.EXTRA_TEXT));
                return;
            }
            checkFile(intent.getParcelableExtra(Intent.EXTRA_STREAM));
        } else if(intent.getAction().equals(Intent.ACTION_VIEW)) {
            checkFile(intent.getData());
        } else {
            Toast.makeText(this, "不支持的操作", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void confirmSendText(String text) {
        runOnUiThread(() -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.text_send_text)
                    .setMessage(R.string.text_send_text_dialog_message)
                    .setPositiveButton(R.string.text_ok, (dialog, which) -> {
                        sendText(text);
                    })
                    .setNegativeButton(R.string.text_cancel, (dialog, which) -> {
                        finish();
                    }).show();
        });
    }

    private boolean checkFromSelf(Intent intent) {
        //标记
        return intent.getBooleanExtra("suisho_share", false);
    }

    /**
     * 检查并询问上传
     */
    private void checkFile(@Nullable Uri data) {
        //以防万一 以及修复一个奇怪的bug
        if(data == null || data.getAuthority().contains("com.android.calendar")) {
            Toast.makeText(this, R.string.text_send_file_not_data, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Thread thread = new Thread(() -> {
            try (ParcelFileDescriptor pickFile = getContentResolver().openFileDescriptor(data, "r")) {
                //获取文件名
                Cursor cursor = getContentResolver().query(data, null, null, null, null, null);
                if(cursor == null || pickFile == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.text_send_file_not_data, Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                cursor.moveToFirst();
                long fileSize = pickFile.getStatSize();
                /*异常的文件大小*/
                if(fileSize == -1) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.text_send_file_not_data, Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }
                String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                cursor.close();
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.text_send_file)
                            .setMessage("确认将文件:\"" + fileName + "\"发送至计算机?")
                            .setPositiveButton("发送", (dialog, which) -> {
                                sendFile(fileName, fileSize, data);
//                                finish();
                            })
                            .setNegativeButton("取消", (dialog, which) -> {
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                });
            } catch (IOException ioe) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "上传文件失败:发生异常", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "不支持的上传类型:" + data.getAuthority(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
        thread.start();
    }

    private void sendFile(String name, long size, Uri file) {
        EncryptionKey encryptionKey;
        try {
            encryptionKey = EncryptionKey.getInstance("AES", 128);
        } catch (NoSuchAlgorithmException e) {
            Log.e("main", e.getMessage(), e);
            runOnUiThread(() -> new MaterialAlertDialogBuilder(FileUploadActivity.this).setTitle("上传文件发生异常")
                    .setMessage("发生异常:" + e.getMessage())
                    .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                    .show());
            return;
        }
        ConnectMainService networkService = GlobalVariables.computerConfigManager.getNetworkService();
        JsonObject uploadRequest = new JsonObject();
        uploadRequest.addProperty("packetType", "action_transmit");
        uploadRequest.addProperty("messageType", "file");
        /*文件名*/
        uploadRequest.addProperty("name", name);
        /*大小*/
        uploadRequest.addProperty("size", size);
        //密钥
        uploadRequest.addProperty("encryptKey", encryptionKey.getKeyBase64());
        //向量
        uploadRequest.addProperty("encryptIv", encryptionKey.getIvBase64());
        networkService.sendRequestPacket(uploadRequest, new RequestHandle() {
            @Override
            public void run(String data) {
                super.run(data);
                /*加上_result字段 分开报错和正常*/
                MainServiceJson jsonObj = GlobalVariables.jsonBuilder.fromJson(data, MainServiceJson.class);
                //检查是否发生异常
                if(jsonObj._result.equals("ERROR")) {
                    //异常
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(FileUploadActivity.this).setTitle("上传文件发生异常")
                            .setMessage(jsonObj.msg)
                            .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                            .show());
                    return;
                }
                try {
                    networkService.uploadFile(getContentResolver().openInputStream(file), jsonObj.port, size <= 8192L, new FileUploadStateHandle() {
                        @Override
                        public void onStart() {
                            super.onStart();
                            FileUploadActivity.this.finish();
                        }

                        @Override
                        public void onError(Exception error) {
                            super.onError(error);
                        }

                        @Override
                        public void onSuccess() {
                            super.onSuccess();
                            notificationManager.cancel(NotificationID.NOTIFICATION_TRANSMIT_UPLOAD_FILE);
                            if(TransmitFragment.transmitMessagesListAdapter == null) return;
                            TransmitDatabaseEntity message = new TransmitDatabaseEntity();
                            message.messageFrom = TransmitMessage.MESSAGE_FROM_PHONE;
                            message.type = TransmitMessage.MESSAGE_TYPE_TEXT;
                            message.isDeleted = false;
                            message.fileName = name;
                            message.fileSize = size;
                            message.timestamp = System.currentTimeMillis();
                            //上传文件 该属性无效
                            message.filePath = "null";
                            TransmitFragment.transmitMessagesListAdapter.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_FILE, new TransmitMessageTypeFile(message));
                        }
                    }, size, encryptionKey);
                } catch (FileNotFoundException e) {
                    if(FileUploadActivity.this.isDestroyed()) return;
                    Notification.Builder builder = new Notification.Builder(FileUploadActivity.this, "fileUploadProgress");
                    builder.setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("文件上传失败")
                            .setContentText(name + "上传失败")
                            .setWhen(System.currentTimeMillis())
                            .setChannelId("fileUploadProgress")
                            .build();
                }
            }
        });
    }

    private void sendText(String text) {
        ConnectMainService networkService = GlobalVariables.computerConfigManager.getNetworkService();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("packetType", "action_transmit");
        jsonObject.addProperty("messageType", "planeText");
        jsonObject.addProperty("data", text);
        networkService.sendString(jsonObject.toString());
        Toast.makeText(this, R.string.text_sent, Toast.LENGTH_LONG).show();
        if(TransmitFragment.transmitMessagesListAdapter == null) return;
        TransmitFragment.transmitMessagesListAdapter.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_TEXT, new TransmitMessageTypeText(text, true));
        finish();
    }
}