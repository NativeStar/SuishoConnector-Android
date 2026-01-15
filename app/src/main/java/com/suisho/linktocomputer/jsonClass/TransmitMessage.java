package com.suisho.linktocomputer.jsonClass;

public class TransmitMessage {
    //常量
    public static final byte MESSAGE_FROM_PHONE=0;
    public static final byte MESSAGE_FROM_COMPUTER=1;
    public static final byte MESSAGE_TYPE_TEXT=0;
    public static final byte MESSAGE_TYPE_FILE=1;
    //消息类型
    public byte type;
    public String msg;
    public byte messageFrom;
    public long fileSize;
    public boolean isDeleted;
    public String fileName;
    public String filePath;
}
