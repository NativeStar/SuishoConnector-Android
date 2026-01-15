package com.suisho.linktocomputer.constant;

public class FileManagerResultCode {
    //正常
    public static final int CODE_NORMAL=0;
    //调用读取文件夹但目标不是文件夹
    public static final int CODE_NOT_DIR=1;
    //读取无权限
    public static final int CODE_NOT_PERMISSION=2;
    //功能被关闭
    public static final int CODE_FUNCTION_DISABLED=3;
    //设备不受信任
    public static final int CODE_DEVICE_NOT_TRUSTED=4;
}
