package com.suisho.linktocomputer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.responseBuilders.DeviceStateUpdatePacket;
import com.suisho.linktocomputer.service.ConnectMainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatteryStateReceiver extends BroadcastReceiver {
    private final ConnectMainService networkService;
    private final Logger logger = LoggerFactory.getLogger(BatteryStateReceiver.class);
    private final PowerManager powerManager;
    private boolean isDeviceIdle = false;
    private DeviceStateUpdatePacket lastStateUpdatePacket;

    public BatteryStateReceiver(ConnectMainService service) {
        this.networkService = service;
        powerManager = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(networkService.isConnected) {
            if(intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                logger.debug("Send battery state update packet");
                DeviceStateUpdatePacket updatePacket = new DeviceStateUpdatePacket(networkService, intent);
                updatePacket.setBatteryLevel(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
                updatePacket.setBatteryTemp(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
                updatePacket.setIsDozeMode(isDeviceIdle);
                //缓存数据包 当doze状态更新时可以直接用
                lastStateUpdatePacket = updatePacket;
                networkService.sendString(updatePacket.build().toString());
                return;
            }
            if(intent.getAction().equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) || intent.getAction().equals(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED)) {
                isDeviceIdle = checkDeviceIdle();
                if(lastStateUpdatePacket != null) {
                    //直接用缓存 没必要单开一种数据包类型了
                    lastStateUpdatePacket.setIsDozeMode(isDeviceIdle);
                    networkService.sendString(lastStateUpdatePacket.build().toString());
                }
            }
        }
    }

    private boolean checkDeviceIdle() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean reportLightDoze = GlobalVariables.preferences.getBoolean("report_light_doze", true);
            if(reportLightDoze) {
                logger.debug("Check device idle mode with light doze");
                return powerManager.isDeviceIdleMode() || powerManager.isDeviceLightIdleMode();
            }
        }
        logger.debug("Check device idle mode with normal");
        return powerManager.isDeviceIdleMode();
    }

}
