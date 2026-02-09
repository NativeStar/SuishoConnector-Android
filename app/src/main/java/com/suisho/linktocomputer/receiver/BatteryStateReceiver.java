package com.suisho.linktocomputer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.PowerManager;

import com.suisho.linktocomputer.responseBuilders.DeviceStateUpdatePacket;
import com.suisho.linktocomputer.service.ConnectMainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatteryStateReceiver extends BroadcastReceiver {
    private final ConnectMainService networkService;
    private final Logger logger = LoggerFactory.getLogger(BatteryStateReceiver.class);
    private final PowerManager powerManager;
    public BatteryStateReceiver(ConnectMainService service) {
        this.networkService=service;
        powerManager= (PowerManager) service.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(networkService.isConnected){
            logger.debug("Send battery state update packet");
            DeviceStateUpdatePacket updatePacket=new DeviceStateUpdatePacket(networkService,intent,powerManager);
            updatePacket.setBatteryLevel(intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1));
            updatePacket.setBatteryTemp(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1));
            networkService.sendString(updatePacket.build().toString());
        }
    }

}
