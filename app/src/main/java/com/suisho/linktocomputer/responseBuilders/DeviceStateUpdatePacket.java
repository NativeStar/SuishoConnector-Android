package com.suisho.linktocomputer.responseBuilders;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.PowerManager;

import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceStateUpdatePacket {
    private final Context appContext;
    private int batteryLevel=-1;
    private int batteryTemp=-1;
    private final Intent broadcastIntent;
    private final PowerManager powerManager;
    private final Logger logger = LoggerFactory.getLogger(DeviceStateUpdatePacket.class);



    public DeviceStateUpdatePacket(Context context, Intent intent, PowerManager powerManager) {
        this.appContext = context;
        this.broadcastIntent = intent;
        this.powerManager = powerManager;
    }
    public void setBatteryLevel(int level) {
        this.batteryLevel = level;
    }

    public JsonObject build() {
        //未被初始化检测
        if(this.batteryLevel==-1){
            this.batteryLevel=getBatteryLevel();
        }
        JsonObject jsonObject=new JsonObject();
        jsonObject.addProperty("packetType","updateDeviceState");
        //电量
        jsonObject.addProperty("batteryLevel",this.batteryLevel);
        //内存信息
        jsonObject.add("memInfo",getMemoryInfo());
        //电池温度
        jsonObject.addProperty("batteryTemp",this.batteryTemp==-1?null:this.batteryTemp);
        //是否充电
        jsonObject.addProperty("charging", isCharging());
        //doze模式
        jsonObject.addProperty("inDoze", powerManager.isDeviceIdleMode());
        logger.debug("Created device state update packet");
        return jsonObject;
    }

    private int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
    private boolean isCharging(){
        return (broadcastIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)!=0);
    }
    public void setBatteryTemp(int temp){
        this.batteryTemp=temp;
    }
    private JsonObject getMemoryInfo() {
        ActivityManager activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        JsonObject jsonObj = new JsonObject();
        //注意类型long
        jsonObj.addProperty("total", memInfo.totalMem);
        jsonObj.addProperty("avail", memInfo.availMem);
        logger.debug("Memory info:{}/{}",memInfo.availMem,memInfo.totalMem);
        return jsonObj;
    }
}
