package com.suisho.linktocomputer.abstracts;

import android.app.Notification;

public abstract class FileUploadStateHandle {
    /**
     * 上传开始
     */
    public void onStart(){}

    /**
     * 上传进度更新回调
     * @param uploadedSize 已上传大小
     */
    public void onProgress(long uploadedSize, Notification.Builder notificationBuilder){}//上传进度 等待完善
    /**
     * 上传完成时
     */
    public void onSuccess(){}

    /**
     * 上传失败
     * @param error 失败对象实例
     */
    public void onError(Exception error){}
}
