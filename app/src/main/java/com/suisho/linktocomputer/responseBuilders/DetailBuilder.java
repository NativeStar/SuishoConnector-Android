package com.suisho.linktocomputer.responseBuilders;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.BatteryManager;

import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DetailBuilder {
    private final String responseId;
    private final Context appContext;
    private final NotificationManager notificationManager;
    private final Logger logger = LoggerFactory.getLogger(DetailBuilder.class);

    public DetailBuilder(String requestId, Context context, NotificationManager notificationManager) {
        this.responseId = requestId;
        this.appContext = context;
        this.notificationManager = notificationManager;
    }

    public JsonObject create() {
        JsonObject obj = new JsonObject();
        //标记为返回包
        obj.addProperty("_isResponsePacket", true);
        //返回id
        obj.addProperty("_responseId", responseId);
        //电量百分比
        obj.addProperty("batteryLevel", getBatteryLevel());
        //内存信息
        obj.add("memoryInfo", getMemoryInfo());
        //勿扰模式
        obj.addProperty("doNotDisturbEnabled", notificationManager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL);
        logger.debug("Created device detail packet");
        return obj;
    }

    //电量
    private int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    //内存信息
    private JsonObject getMemoryInfo() {
        ActivityManager activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        JsonObject jsonObj = new JsonObject();
        //注意类型long
        jsonObj.addProperty("total", memInfo.totalMem);
        jsonObj.addProperty("avail", memInfo.availMem);
        logger.debug("Memory info:{}/{}", memInfo.availMem, memInfo.totalMem);
        return jsonObj;
    }
}
