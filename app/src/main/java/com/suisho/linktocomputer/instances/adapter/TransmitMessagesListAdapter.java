package com.suisho.linktocomputer.instances.adapter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.R;
import com.suisho.linktocomputer.Util;
import com.suisho.linktocomputer.abstracts.TransmitMessageAbstract;
import com.suisho.linktocomputer.activity.NewMainActivity;
import com.suisho.linktocomputer.constant.MessageConf;
import com.suisho.linktocomputer.database.TransmitDatabaseEntity;
import com.suisho.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.suisho.linktocomputer.instances.transmit.TransmitMessageTypeFile;
import com.suisho.linktocomputer.instances.transmit.TransmitMessageTypeText;
import com.suisho.linktocomputer.provider.ShareFileProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.objectbox.Box;

public class TransmitMessagesListAdapter extends RecyclerView.Adapter<TransmitMessagesListAdapter.ViewHolder> {
    private final List<TransmitMessageAbstract> dataList = new ArrayList<>();
    private final ClipboardManager clipboardManager;
    private final Box<TransmitDatabaseEntity> database;

    public RecyclerView getView() {
        return messagesView;
    }

    public int getDataSize() {
        return dataList.size();
    }

    private final RecyclerView messagesView;
    private Activity activity;
    private final Logger logger = LoggerFactory.getLogger(TransmitMessagesListAdapter.class);
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case MessageConf.MESSAGE_TYPE_TEXT:
                return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_transmit_text_item_view, parent, false));
            case MessageConf.MESSAGE_TYPE_FILE:
                return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_transmit_file_item_view, parent, false));
            default:
                logger.warn("Unknown view type!");
                return null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        //获取类型
        return dataList.get(position).getType();
    }

    public TransmitMessagesListAdapter(List<TransmitDatabaseEntity> data, Activity activity, Box<TransmitDatabaseEntity> databaseBox, RecyclerView recyclerView) {
        messagesView = recyclerView;
        database = databaseBox;
        messagesView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                //到底部消除提示图标
                if(!messagesView.canScrollVertically(1)) {
                    BottomNavigationView bottomNavigationView = activity.findViewById(R.id.connected_activity_navigation_bar);
                    bottomNavigationView.removeBadge(R.id.connected_activity_navigation_bar_menu_transmit);
                    logger.debug("User scroll to bottom.Remove new message badge");
                }
            }
        });
        //
        this.activity = activity;
        clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        data.forEach(transmitMessage -> {
            switch (transmitMessage.type) {
                case MessageConf.MESSAGE_TYPE_FILE:
                    //文件
                    dataList.add(new TransmitMessageTypeFile(transmitMessage));
                    break;
                case MessageConf.MESSAGE_TYPE_TEXT:
                    dataList.add(new TransmitMessageTypeText(transmitMessage));
                    break;
                default:
                    logger.warn("Unknown message type!");
                    break;
            }
        });
        //加载完成
        activity.runOnUiThread(() -> {
            activity.findViewById(R.id.transmit_message_list).requestLayout();
        });
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case MessageConf.MESSAGE_TYPE_TEXT:
                logger.debug("Init text message");
                TextView textView = holder.messageView.findViewById(R.id.transmit_message_text_view);
                TextView timeTextView = holder.messageView.findViewById(R.id.transmit_message_text_time);
                TransmitMessageTypeText textMessageInstance = (TransmitMessageTypeText) dataList.get(position);
                String text = textMessageInstance.msg;
                String time = simpleDateFormat.format(textMessageInstance.timestamp);
                textView.setText(text);
                timeTextView.setText(time);
                final boolean isUrl = !text.isEmpty() && Patterns.WEB_URL.matcher(text).matches();
                holder.messageView.setOnLongClickListener(view -> {
                    logger.debug("Text message long clicked.Show menu");
                    //文本卡片长按事件
                    View menuLayout = LayoutInflater.from(activity).inflate(R.layout.transmit_message_action_menu_text, null, false);
                    PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
                    setUniversalLongClickMenuAction(menuLayout, messagesView, popupWindow, holder);
                    menuLayout.findViewById(R.id.long_click_menu_action_open_url).setEnabled(isUrl);
                    menuLayout.findViewById(R.id.long_click_menu_action_open_url).setOnClickListener(v -> {
                        Intent urlIntent = new Intent(Intent.ACTION_VIEW);
                        urlIntent.setData(Uri.parse(text));
                        activity.startActivity(urlIntent);
                    });
                    menuLayout.findViewById(R.id.long_click_menu_action_copy_full).setOnClickListener(v -> {
                        logger.info("Copy text message full");
                        popupWindow.dismiss();
                        ClipData clipData = ClipData.newPlainText("CopyText", text);
                        clipboardManager.setPrimaryClip(clipData);
                        logger.debug("Copy full message:{}", clipData);
                        Snackbar.make(((NewMainActivity) activity).getBinding().getRoot(), "已复制", 2000).show();
                    });
                    menuLayout.findViewById(R.id.long_click_menu_action_copy_free).setOnClickListener(v -> {
                        logger.debug("Copy text message free");
                        popupWindow.dismiss();
                        textView.setTextIsSelectable(true);
                        textView.setFocusable(true);
                        textView.setFocusableInTouchMode(true);
                    });
                    showPopupMenu(popupWindow, view, menuLayout);
                    return true;
                });
                //来自手机的消息额外处理
                if(((TransmitMessageTypeText) dataList.get(position)).messageFrom == MessageConf.MESSAGE_FROM_PHONE) {
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.messageView.getLayoutParams();
                    params.setMarginStart(Util.dp2px(110));
                    holder.messageView.setLayoutParams(params);
                    holder.messageView.requestLayout();
                } else {
                    //修复手机端发消息后接收pc消息位置异常
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.messageView.getLayoutParams();
                    params.setMarginStart(Util.dp2px(10));
                    holder.messageView.setLayoutParams(params);
                    holder.messageView.requestLayout();
                }
                //清除焦点
                holder.messageView.setOnClickListener(View::clearFocus);
                break;
            case MessageConf.MESSAGE_TYPE_FILE:
                logger.debug("Init file message");
                TransmitMessageTypeFile messageInstance = (TransmitMessageTypeFile) dataList.get(position);
                //文件名
                ((TextView) holder.messageView.findViewById(R.id.transmit_file_name)).setText(messageInstance.fileName);
                //文件大小
                ((TextView) holder.messageView.findViewById(R.id.transmit_file_size)).setText(Util.coverFileSize(messageInstance.fileSize));
                //时间
                ((TextView) holder.messageView.findViewById(R.id.transmit_file_time)).setText(simpleDateFormat.format(messageInstance.timestamp));
                //是本地上传的文件 无法打开 不显示图标
                logger.debug("File message name:{},size:{}", messageInstance.fileName, messageInstance.fileSize);
                if(messageInstance.messageFrom == MessageConf.MESSAGE_FROM_PHONE) {
                    (holder.messageView.findViewById(R.id.transmit_file_openable_icon)).setVisibility(View.GONE);
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.messageView.getLayoutParams();
                    params.setMarginStart(Util.dp2px(143));
                    holder.messageView.setLayoutParams(params);
                    holder.messageView.requestLayout();
                } else {
                    (holder.messageView.findViewById(R.id.transmit_file_openable_icon)).setVisibility(View.GONE);
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.messageView.getLayoutParams();
                    params.setMarginStart(Util.dp2px(10));
                    holder.messageView.setLayoutParams(params);
                    (holder.messageView.findViewById(R.id.transmit_file_openable_icon)).setVisibility(View.VISIBLE);
                    holder.messageView.requestLayout();
                }
                //注册点击事件 只有接收的文件才能打开
                holder.messageView.setOnClickListener(v -> {
                    if(messageInstance.messageFrom == MessageConf.MESSAGE_FROM_PHONE) return;
                    //检查内容为"null"的字符串及真的null
                    if(messageInstance.filePath == null || messageInstance.filePath.equals("null")) {
                        //文件路径为空
                        logger.warn("File message path is null!");
                        Snackbar.make(activity, ((NewMainActivity) activity).getBinding().getRoot(), activity.getResources().getText(R.string.transmit_open_file_failed_null_path), 2000).show();
                        return;
                    }
                    File file = new File(messageInstance.filePath);
                    //检测文件是否存在
                    if(!file.exists()) {
                        logger.debug("File message path '{}' deleted", file.getPath());
                        //不存在
                        Snackbar.make(activity, ((NewMainActivity) activity).getBinding().getRoot(), activity.getResources().getText(R.string.transmit_open_file_failed_not_exists), 2000).show();
                        return;
                    }
                    File openTarget = new File(messageInstance.filePath);
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    //打开文件功能也注册了发送 标记防止发送自身文件
                    intent.putExtra("suisho_share", true);
                    Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".transmitOpenFileProvider", openTarget);
                    FileNameMap fileNameMap = URLConnection.getFileNameMap();
                    intent.setDataAndType(uri, fileNameMap.getContentTypeFor(openTarget.getName()));
                    if(intent.resolveActivity(activity.getPackageManager()) != null) {
                        logger.debug("Open transmit file");
                        activity.startActivity(intent);
                    } else {
                        logger.info("File message can not resolve application to open it");
                        Snackbar.make(activity, ((NewMainActivity) activity).getBinding().getRoot(), activity.getResources().getText(R.string.transmit_open_file_failed_not_resolve_application), 2000).show();
                    }
                });
                holder.messageView.setOnLongClickListener(view -> {
                    View menuLayout = LayoutInflater.from(activity).inflate(R.layout.transmit_message_action_menu_file, null, false);
                    PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
                    //通用功能
                    setUniversalLongClickMenuAction(menuLayout, messagesView, popupWindow, holder);
                    //长按自己发送的文件时屏蔽分享键
                    if(messageInstance.messageFrom == MessageConf.MESSAGE_FROM_PHONE) {
                        menuLayout.findViewById(R.id.long_click_menu_action_share).setEnabled(false);
                    }
                    //分享 文件专属
                    menuLayout.findViewById(R.id.long_click_menu_action_share).setOnClickListener(v -> activity.runOnUiThread(() -> {
                        //兜底
                        if(messageInstance.filePath == null || messageInstance.filePath.equals("null")) {
                            //应该没错
                            logger.info("Share file message but path is null");
                            Snackbar.make(((NewMainActivity) activity).getBinding().getRoot(), "无法分享来自自身的文件", 2000).show();
                            return;
                        }
                        File file = new File(messageInstance.filePath);
                        //检测文件是否存在
                        if(!file.exists()) {
                            //不存在
                            Snackbar.make(activity, ((NewMainActivity) activity).getBinding().getRoot(), activity.getResources().getText(R.string.transmit_open_file_failed_not_exists), 2000).show();
                            popupWindow.dismiss();
                            logger.debug("Share file failed because file deleted");
                            return;
                        }
                        logger.debug("Request share file:{}", file.getPath());
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        //有重复的文件 使用专门的provider而不是displayName参数防止部分软件依旧获取到错误的文件名
                        //说的就是你 微信
                        Uri uri;
                        if(messageInstance.filePath.endsWith("/" + messageInstance.fileName)) {
                            //正常分享
                            uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".transmitOpenFileProvider", new File(messageInstance.filePath));
                            logger.debug("Not repeat file name.Using system provider");
                        } else {
                            //自己的 provider
                            logger.debug("Has repeat file name.Using custom provider");
                            ShareFileProvider.setShareFileName(messageInstance.fileName);
                            uri = ShareFileProvider.getUriForFile(activity, activity.getPackageName() + ".ShareFileProvider", new File(messageInstance.filePath));
                        }
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.putExtra(Intent.EXTRA_TITLE, messageInstance.fileName);
                        intent.putExtra(Intent.EXTRA_TEXT, messageInstance.fileName);
                        //标记 阻止发送自己的东西
                        intent.putExtra("suisho_share", true);
                        FileNameMap fileNameMap = URLConnection.getFileNameMap();
                        intent.setDataAndType(uri, fileNameMap.getContentTypeFor(messageInstance.fileName));
                        if(intent.resolveActivity(activity.getPackageManager()) != null) {
                            activity.startActivity(Intent.createChooser(intent, null));
                            logger.debug("Start share file activity");
                        } else {
                            logger.info("File message can not resolve application to share it");
                            Snackbar.make(activity, ((NewMainActivity) activity).getBinding().getRoot(), "找不到支持分享的应用程序", 2000).show();
                        }
                        popupWindow.dismiss();
                    }));
                    showPopupMenu(popupWindow, view, menuLayout);
                    return true;
                });
                break;
            default:
                break;
        }
    }

    /**
     * @param menu        菜单view
     * @param messageView 被长按的消息view
     * @param popup       弹出菜单本身
     */
    private void setUniversalLongClickMenuAction(View menu, View messageView, PopupWindow popup, ViewHolder holder) {
        //删除
        menu.findViewById(R.id.long_click_menu_action_delete).setOnClickListener(v -> activity.runOnUiThread(() -> {
            popup.dismiss();
            new MaterialAlertDialogBuilder(activity)
                    .setMessage("确认删除该消息?")
                    .setPositiveButton("确定", (dialog, which) -> {
                        //删除
                        dialog.dismiss();
                        TransmitMessageAbstract transmitMessageAbstract = dataList.get(holder.getLayoutPosition());
                        database.remove(transmitMessageAbstract.timestamp);
                        dataList.remove(holder.getLayoutPosition());
                        //通知移除和保存
                        notifyItemRemoved(holder.getLayoutPosition());
                    }).setNegativeButton("取消", (dialog, which) -> dialog.cancel())/*啥事没有*/.show();
        }));
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    //外部调用 追加数据
    public void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract pushData) {
        //默认请求保存
        addItem(type, pushData, true, false);
    }

    /**
     * @param type                消息类型 暂时没用上
     * @param pushData            数据主体
     * @param requestSave         是否需要保存数据
     * @param forceScrollToBottom 是否强制滚动到底部
     */
    public void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract pushData, boolean requestSave, boolean forceScrollToBottom, @Nullable RecyclerView recyclerView) {
        logger.debug("Adding item to transmit message list");
        //type预留做前处理
        //追加
        dataList.add(pushData);
        //更新列表
        if(!activity.isDestroyed()) {
            //防止通过系统分享上传文件时在非ui线程执行崩溃
            activity.runOnUiThread(() -> {
                notifyItemInserted(dataList.size() - 1);
                //滑动到底部
                listScrollToBottom(forceScrollToBottom, recyclerView);
            });
        }
        //保存
        if(requestSave && enableSaveHistory()) database.put(pushData.toDatabaseEntity());
    }

    public void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract pushData, boolean requestSave, boolean forceScrollToBottom) {
        addItem(type, pushData, requestSave, forceScrollToBottom, null);
    }

    public void listScrollToBottom(boolean force, @Nullable RecyclerView recyclerView) {
        logger.debug("Request scroll transmit list to bottom.Force:{}", force);
        RecyclerView scrollView = messagesView == null ? activity.findViewById(R.id.transmit_message_list) : messagesView;
        if(scrollView == null) return;
        scrollView.clearOnScrollListeners();
        scrollView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                //到底部消除提示图标
                if(!scrollView.canScrollVertically(1)) {
                    logger.debug("Auto scroll to bottom.Remove new message badge");
                    BottomNavigationView bottomNavigationView = activity.findViewById(R.id.connected_activity_navigation_bar);
                    bottomNavigationView.removeBadge(R.id.connected_activity_navigation_bar_menu_transmit);
                }
            }
        });
        //强制拉到底部
        if(force) {
            scrollView.scrollToPosition(dataList.size() - 1);
            logger.debug("Force scroll to bottom");
            return;
        }
        //距离过小 往下拉
        if(scrollView.computeVerticalScrollRange() - (scrollView.computeVerticalScrollExtent() + scrollView.computeVerticalScrollOffset()) <= 300) {
            listScrollToBottom(true, scrollView);
            logger.debug("received new message and low bottom range.Auto scroll");
        } else {
            //新消息红点
            logger.debug("Received new transmit message and not auto scroll.Add new message badge");
            BottomNavigationView bottomNavigationView = activity.findViewById(R.id.connected_activity_navigation_bar);
            bottomNavigationView.getOrCreateBadge(R.id.connected_activity_navigation_bar_menu_transmit);
        }
    }

    protected static class ViewHolder extends RecyclerView.ViewHolder {
        public final View messageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            messageView = itemView;
        }
    }

    private boolean enableSaveHistory() {
        return GlobalVariables.preferences.getBoolean("transmit_save_history", true);
    }

    private void showPopupMenu(PopupWindow popupWindow, View parent, View menuLayout) {
        popupWindow.setOutsideTouchable(true);
        Rect visibleFrame = new Rect();
        parent.getWindowVisibleDisplayFrame(visibleFrame);
        menuLayout.measure(View.MeasureSpec.makeMeasureSpec(visibleFrame.width(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(visibleFrame.height(), View.MeasureSpec.AT_MOST));
        int popupHeight = menuLayout.getMeasuredHeight();
        int[] location = new int[2];
        parent.getLocationOnScreen(location);
        int anchorTop = location[1];
        int anchorBottom = anchorTop + parent.getHeight();
        int spaceBelow = visibleFrame.bottom - anchorBottom;
        //向下
        if(spaceBelow >= popupHeight || spaceBelow >= anchorTop - visibleFrame.top) {
            popupWindow.showAsDropDown(parent);
        } else {
            //向上
            popupWindow.showAsDropDown(parent, 0, -(parent.getHeight() + popupHeight));
        }
    }

    public void setActivity(Activity act) {
        this.activity = act;
    }
}
