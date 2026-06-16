package com.suisho.linktocomputer.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.interfaces.IBroadcastReceiver;
import com.suisho.linktocomputer.responseBuilders.DeviceStateUpdatePacket;
import com.suisho.linktocomputer.service.ConnectMainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceStateReceiver extends BroadcastReceiver implements IBroadcastReceiver {
    private final ConnectMainService networkService;
    private final Logger logger = LoggerFactory.getLogger(DeviceStateReceiver.class);
    private final PowerManager powerManager;
    private final NotificationManager notificationManager;
    private boolean isDeviceIdle = false;
    private boolean isDoNotDisturb = false;
    private DeviceStateUpdatePacket lastStateUpdatePacket;

    public DeviceStateReceiver(ConnectMainService service, int interruptionFilter) {
        this.networkService = service;
        isDoNotDisturb = interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL;
        powerManager = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
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
                //缓存数据包 当其他状态更新时可以直接用
                lastStateUpdatePacket = updatePacket;
                networkService.sendObject(updatePacket.build());
                return;
            }
            if(intent.getAction().equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) || intent.getAction().equals(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED)) {
                isDeviceIdle = checkDeviceIdle();
                if(lastStateUpdatePacket != null) {
                    //直接用缓存 没必要单开一种数据包类型了
                    logger.debug("Send doze mode update packet.Mode:{}", isDeviceIdle);
                    lastStateUpdatePacket.setIsDozeMode(isDeviceIdle);
                    networkService.sendObject(lastStateUpdatePacket.build());
                }
                return;
            }
            if(intent.getAction().equals(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) {
                isDoNotDisturb = notificationManager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
                if(lastStateUpdatePacket != null) {
                    logger.debug("Send do not disturb update packet.Mode:{}", isDoNotDisturb);
                    lastStateUpdatePacket.setIsDoNotDisturb(isDoNotDisturb);
                    networkService.sendObject(lastStateUpdatePacket.build());
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

    public static IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            filter.addAction(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED);
        }
        //勿扰模式
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        return filter;
    }
}
