package com.example.linktocomputer.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linktocomputer.Crystal;
import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.abstracts.FileUploadStateHandle;
import com.example.linktocomputer.abstracts.RequestHandle;
import com.example.linktocomputer.abstracts.TransmitMessageAbstract;
import com.example.linktocomputer.constant.NotificationID;
import com.example.linktocomputer.database.TransmitDatabaseEntity;
import com.example.linktocomputer.databinding.FragmentTransmitBinding;
import com.example.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.example.linktocomputer.instances.EncryptionKey;
import com.example.linktocomputer.instances.adapter.TransmitMessagesListAdapter;
import com.example.linktocomputer.instances.transmit.TransmitMessageTypeFile;
import com.example.linktocomputer.instances.transmit.TransmitMessageTypeText;
import com.example.linktocomputer.jsonClass.MainServiceJson;
import com.example.linktocomputer.jsonClass.TransmitMessage;
import com.example.linktocomputer.network.TransmitUploadFile;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import io.objectbox.Box;

public class TransmitFragment extends Fragment {
    private static FragmentTransmitBinding binding;
    //是否已完成初始化
    private boolean isInit = false;

    //跨进程通信
    private static ConnectMainService networkService;
    private Activity activity;

    //互传内容列表适配器
    //static不能删 否则activity重建之后pc端消息无法实时显示
    public static TransmitMessagesListAdapter transmitMessagesListAdapter;
    private NotificationManager notificationManager;

    public TransmitMessagesListAdapter getTransmitMessagesListAdapter() {
        return transmitMessagesListAdapter;
    }

    public TransmitFragment() {
        // Required empty public constructor
    }

    /*所有返回类型*/
    private final int ACTIVITY_RESULT_IMAGE_PICK = 0;/*图片*/
    private final int ACTIVITY_RESULT_FILE_PICK = 1;/*文件*/


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            //重启时直接获取
            //就这样吧这方法有概率返回null
            activity = getActivity();
        }
        //修复activity被销毁后用户操作列表崩溃
        if(transmitMessagesListAdapter != null) transmitMessagesListAdapter.setActivity(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTransmitBinding.inflate(inflater);
        //初始化
        init();
        if(!isInit) {
            isInit = true;
        }
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!isInit) initTransmitMessages(null);

    }

    public void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract pushData, boolean requestSave, boolean forceScrollToBottom) {
        transmitMessagesListAdapter.addItem(type, pushData, requestSave, forceScrollToBottom);
        scrollMessagesViewToBottom(false);
    }

    public void scrollMessagesViewToBottom(boolean force) {
        activity.runOnUiThread(() -> {
            if(force) {
                TransmitMessagesListAdapter transmitMessagesListAdapter = (TransmitMessagesListAdapter) binding.transmitMessageList.getAdapter();
                binding.transmitMessageList.scrollToPosition(transmitMessagesListAdapter.getDataSize() - 1);
                return;
            }
            if(binding.transmitMessageList.computeVerticalScrollRange() - (binding.transmitMessageList.computeVerticalScrollExtent() + binding.transmitMessageList.computeVerticalScrollOffset()) <= 300) {
                scrollMessagesViewToBottom(true);
            } else {
                //新消息红点
                BottomNavigationView bottomNavigationView = activity.findViewById(R.id.connected_activity_navigation_bar);
                bottomNavigationView.getOrCreateBadge(R.id.connected_activity_navigation_bar_menu_transmit);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(data == null) {
            //啥都没做
            return;
        }
        if(networkService == null || !networkService.isConnected) {
            /*掉线了*/
            Snackbar.make(activity.findViewById(R.id.transmit_message_list), R.string.transmit_send_failed_network, 2000).show();
            return;
        }
        switch (requestCode) {
            case ACTIVITY_RESULT_IMAGE_PICK:/*图片*/
//                两个仅仅是选择器有区别 可能后面代码能合一起
//                break;
            case ACTIVITY_RESULT_FILE_PICK:/*文件*/
                try {
                    ParcelFileDescriptor pickFile = getContext().getContentResolver().openFileDescriptor(data.getData(), "r");
                    //获取文件名
                    Cursor cursor = getContext().getContentResolver().query(data.getData(), null, null, null, null, null);
                    cursor.moveToFirst();
                    long fileSize = pickFile.getStatSize();
                    /*异常的文件大小*/
                    if(fileSize == -1) {
                        throw new FileNotFoundException("File size is -1");
                    }
                    String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    cursor.close();
                    //加密key
                    EncryptionKey encryptionKey;
                    try {
                        encryptionKey = EncryptionKey.getInstance("AES", 128);
                    } catch (NoSuchAlgorithmException e) {
                        activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(getContext()).setTitle("上传文件异常")
                                .setMessage(e.getMessage())
                                .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                                .show());
                        return;
                    }
                    //构建请求
                    JsonObject uploadRequestObject = new JsonObject();
                    /*基础参数*/
                    uploadRequestObject.addProperty("packetType", "action_transmit");
                    uploadRequestObject.addProperty("messageType", "file");
                    /*文件名*/
                    uploadRequestObject.addProperty("name", fileName);
                    /*大小*/
                    uploadRequestObject.addProperty("size", fileSize);
                    //密钥
                    uploadRequestObject.addProperty("encryptKey", encryptionKey.getKeyBase64());
                    //向量
                    uploadRequestObject.addProperty("encryptIv", encryptionKey.getIvBase64());
                    //发送请求
                    networkService.sendRequestPacket(uploadRequestObject, new RequestHandle() {
                        @Override
                        public void run(String responseData) {
                            super.run(responseData);
                            /*加上_result字段 分开报错和正常*/
                            MainServiceJson jsonObj = GlobalVariables.jsonBuilder.fromJson(responseData, MainServiceJson.class);
                            //检查是否发生异常
                            if(jsonObj._result.equals("ERROR")) {
                                //异常
                                activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(getContext()).setTitle("上传文件异常")
                                        .setMessage(jsonObj.msg)
                                        .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                                        .show());
                                return;
                            }
                            try {
                                //上传文件
                                networkService.uploadFile(getContext().getContentResolver().openInputStream(data.getData()), jsonObj.port, fileSize <= 8192L, new FileUploadStateHandle() {
                                    @Override
                                    //上传服务异常处理
                                    public void onError(Exception error) {
                                        super.onError(error);
                                        activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(getContext()).setTitle("上传文件异常")
                                                .setMessage(error.getMessage())
                                                .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                                                .show());
                                    }

                                    @Override
                                    public void onSuccess() {
                                        super.onSuccess();
                                        //activity是否被销毁
                                        if(getActivity() == null || getActivity().isDestroyed())
                                            return;
                                        Snackbar.make(activity.findViewById(R.id.transmit_message_list), "上传完成", 2000).show();
                                        notificationManager.cancel(NotificationID.NOTIFICATION_TRANSMIT_UPLOAD_FILE);
                                        TransmitDatabaseEntity message = new TransmitDatabaseEntity();
                                        message.messageFrom = TransmitMessage.MESSAGE_FROM_PHONE;
                                        message.type = TransmitMessage.MESSAGE_TYPE_FILE;
                                        message.isDeleted = false;
                                        message.fileName = fileName;
                                        message.fileSize = fileSize;
                                        //上传文件 该属性无效
                                        message.filePath = "null";
                                        message.timestamp = System.currentTimeMillis();
                                        transmitMessagesListAdapter.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_FILE, new TransmitMessageTypeFile(message));
                                        scrollMessagesViewToBottom(false);
                                    }

                                    @Override
                                    public void onProgress(long uploadedSize, Notification.Builder notificationBuilder) {
                                        //每16次更新触发
                                        super.onProgress(uploadedSize, notificationBuilder);
                                        int uploadProgress = (int) (((float) uploadedSize / fileSize) * 100);
                                        notificationBuilder.setProgress(100, uploadProgress, false);
                                        notificationBuilder.setContentText("已上传" + uploadProgress + "%");
                                        notificationManager.notify(1, notificationBuilder.build());
                                    }
                                }, fileSize, encryptionKey);
                                //打开输入流捕捉
                            } catch (FileNotFoundException | NullPointerException e) {
                                new MaterialAlertDialogBuilder(getContext()).setTitle("打开文件异常")
                                        .setMessage(e.getMessage())
                                        .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                                        .show();
                            }
                        }
                    });
                    //整个方法
                } catch (FileNotFoundException | NullPointerException fe) {
                    Log.i("main", "error", fe);
                    new MaterialAlertDialogBuilder(getContext()).setTitle("打开文件异常")
                            .setMessage(fe.getMessage())
                            .setPositiveButton("确认", (dialog, which) -> dialog.cancel())
                            .show();
                }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void init() {
        notificationManager = getActivity().getSystemService(NotificationManager.class);
        binding.getRoot().post(() -> {
            binding.sendMessageInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    //更改发送按钮
                    if(!Objects.requireNonNull(binding.sendMessageInput.getText()).toString().isEmpty()) {
                        //有文本
                        binding.sendMoreButton.setVisibility(View.GONE);
                        binding.sendMessageButton.setVisibility(View.VISIBLE);
                    } else {
                        //没有
                        binding.sendMoreButton.setVisibility(View.VISIBLE);
                        binding.sendMessageButton.setVisibility(View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
            //发送文字消息按钮
            binding.sendMessageButton.setOnClickListener((view) -> {
                String inputMessage = Objects.requireNonNull(binding.sendMessageInput.getText()).toString();
                //以防万一
                if(inputMessage.isEmpty() || inputMessage.trim().isEmpty()) {
                    return;
                }
                if(networkService == null || !networkService.isConnected) {
                    /*掉线了*/
                    Snackbar.make(activity.findViewById(R.id.transmit_message_list), R.string.transmit_send_failed_network, 2000).show();
                    return;
                }
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("packetType", "action_transmit");
                jsonObject.addProperty("messageType", "planeText");
                jsonObject.addProperty("data", inputMessage);
                networkService.sendString(jsonObject.toString());
                transmitMessagesListAdapter.addItem(TransmitRecyclerAddItemType.ITEM_TYPE_TEXT, new TransmitMessageTypeText(inputMessage), true, true);
                scrollMessagesViewToBottom(false);
                binding.sendMessageInput.setText("");
            });
            //更多类型按钮
            binding.sendMoreButton.setOnClickListener((view) -> {
                View menuLayout = LinearLayout.inflate(getContext(), R.layout.send_more_dialog_layout, null);
                PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
                popupWindow.setOutsideTouchable(true);
                //上传文件按钮点击
                menuLayout.findViewById(R.id.uploadFileButton).setOnClickListener((uploadFileButtonView) -> {
                    popupWindow.dismiss();
                    if(TransmitUploadFile.hasUploadingFile) {
                        Toast.makeText(networkService, R.string.transmit_upload_failed_has_uploading_file, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Intent filePickerIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    filePickerIntent.setType("*/*");
                    startActivityForResult(filePickerIntent, ACTIVITY_RESULT_FILE_PICK);
                });
                menuLayout.findViewById(R.id.uploadImageButtton).setOnClickListener(buttonView -> {
                    popupWindow.dismiss();
                    //检查文件上传
                    if(TransmitUploadFile.hasUploadingFile) {
                        Toast.makeText(networkService, R.string.transmit_upload_failed_has_uploading_file, Toast.LENGTH_LONG).show();
                        return;
                    }
                    //安卓版本
                    Intent intent;
                    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                    } else {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    }
                    intent.setType("image/*");
                    startActivityForResult(intent, ACTIVITY_RESULT_IMAGE_PICK);

                });
                popupWindow.showAsDropDown(binding.sendMoreButton, -(binding.sendMoreButton.getWidth() + 50), -(binding.sendMoreButton.getHeight() * 2));
            });
            //设置列表
            //如果再出现性能问题就改成GridLayoutManager
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);
            binding.transmitMessageList.setLayoutManager(layoutManager);
            //列表各项目间距
            binding.transmitMessageList.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    super.getItemOffsets(outRect, view, parent, state);
                    outRect.bottom = 15;
                }
            });
            binding.transmitMessageList.setAdapter(transmitMessagesListAdapter);
            if(transmitMessagesListAdapter == null) {
                //初始化和activity被杀
                initTransmitMessages(() -> {
                    binding.transmitMessageList.scrollToPosition(transmitMessagesListAdapter.getItemCount() - 1);
                    //重新设置adapter
                    binding.transmitMessageList.setAdapter(transmitMessagesListAdapter);
                    Util.buildAppListCache(activity);
                });
            } else {
                //正常加载
                binding.transmitMessageList.scrollToPosition(transmitMessagesListAdapter.getItemCount() - 1);
            }
            //滑动监听
            binding.transmitMessageList.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    //到底部消除提示图标
                    if(!binding.transmitMessageList.canScrollVertically(1)) {
                        BottomNavigationView bottomNavigationView = activity.findViewById(R.id.connected_activity_navigation_bar);
                        bottomNavigationView.removeBadge(R.id.connected_activity_navigation_bar_menu_transmit);
                    }
                }
            });
            binding.transmitMessageList.setOnTouchListener((v, event) -> {
                //清除焦点
                if(binding.getRoot().hasFocus()) v.clearFocus();
                return false;
            });
        });
    }

    public void setNetworkService(ConnectMainService service) {
        networkService = service;
    }

    public void setActivity(Activity context) {
        activity = context;
    }

    public void initTransmitMessages(@Nullable Runnable onSuccess) {
        //读取数据
        new Thread() {
            @Override
            public void run() {
                super.run();
                File transmitDataPath = new File(activity.getExternalFilesDir(null).getAbsolutePath() + "/transmit/");
                //检查存在
                if(!transmitDataPath.exists()) {
                    //创建它
                    transmitDataPath.mkdirs();
                }
                Box<TransmitDatabaseEntity> transmitBoxStore = ((Crystal) activity.getApplication()).getDatabase().boxFor(TransmitDatabaseEntity.class);
                if(transmitMessagesListAdapter == null) {
                    transmitMessagesListAdapter = new TransmitMessagesListAdapter(transmitBoxStore.getAll(), activity, transmitBoxStore, binding.transmitMessageList);
                }
                if(onSuccess != null) {
                    activity.runOnUiThread(onSuccess);
                }
            }
        }.start();
    }
}