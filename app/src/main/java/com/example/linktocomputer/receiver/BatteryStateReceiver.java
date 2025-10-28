package com.example.linktocomputer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.example.linktocomputer.responseBuilders.DeviceStateUpdatePacket;
import com.example.linktocomputer.service.ConnectMainService;

public class BatteryStateReceiver extends BroadcastReceiver {
    private final ConnectMainService networkService;
    public BatteryStateReceiver(ConnectMainService service) {
        this.networkService=service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(networkService.isConnected){
            DeviceStateUpdatePacket updatePacket=new DeviceStateUpdatePacket(networkService,intent);
            updatePacket.setBatteryLevel(intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1));
            updatePacket.setBatteryTemp(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1));
            networkService.sendString(updatePacket.build().toString());
        }
    }

}
