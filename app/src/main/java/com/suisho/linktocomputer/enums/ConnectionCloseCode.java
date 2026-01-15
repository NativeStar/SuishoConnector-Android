package com.suisho.linktocomputer.enums;

public class ConnectionCloseCode {
    //手机端手动关闭
    public final static int CloseFromClient =1000;
    //pc端手动关闭
    public final static int CloseFromServer=1001;
    //手机端异常关闭
    public final static int CloseFromClientError=1002;
    //手机端wifi断开
    public final static int CloseFromClientWifiDisconnect=1003;
    //手机端崩溃
    public final static int CloseFromClientCrash=1007;
}
