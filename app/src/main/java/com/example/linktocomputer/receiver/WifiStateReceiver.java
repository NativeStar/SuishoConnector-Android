package com.example.linktocomputer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.linktocomputer.service.ConnectMainService;

public class WifiStateReceiver extends BroadcastReceiver {
    private final ConnectMainService networkService;
    public WifiStateReceiver(ConnectMainService service) {
        networkService=service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //本来想搞断开一瞬间向pc发送数据提醒掉线的 实在做不到
        //发个通知算了 也有点儿用
    }
}
