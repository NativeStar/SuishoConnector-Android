package com.example.linktocomputer.responseBuilders;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import com.example.linktocomputer.service.NotificationListenerService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class CurrentNotificationsListPacket {
    public JsonObject build(String requestId,StatusBarNotification[] notifications, NotificationListenerService listenerService){
        JsonObject jsonObject=new JsonObject();
        JsonArray notificationList=new JsonArray();
        for(StatusBarNotification statusBarNotification : notifications) {
            Notification notification=statusBarNotification.getNotification();
            final String notificationTitle=notification.extras.getString(Notification.EXTRA_TITLE);
            final String notificationContent=notification.extras.getString(Notification.EXTRA_TEXT);
            //跳过没有内容的通知 可能用了自定义view 反正传过去没法正常显示的
            if(notificationTitle==null&&notificationContent==null){
                continue;
            }
            JsonObject notificationJsonObject=new JsonObject();
            notificationJsonObject.addProperty("packageName",statusBarNotification.getPackageName());
            notificationJsonObject.addProperty("isOngoing",statusBarNotification.isOngoing());
            notificationJsonObject.addProperty("appName",listenerService.getCachedAppName(statusBarNotification.getPackageName()));
            notificationJsonObject.addProperty("time",statusBarNotification.getPostTime());
            notificationJsonObject.addProperty("key",statusBarNotification.getKey());
            notificationJsonObject.addProperty("title",notificationTitle);
            notificationJsonObject.addProperty("content",notificationContent);
            notificationList.add(notificationJsonObject);
        }
        jsonObject.addProperty("_isResponsePacket",true);
        jsonObject.addProperty("_responseId",requestId);
        jsonObject.add("list",notificationList);
        return jsonObject;
    }
}
