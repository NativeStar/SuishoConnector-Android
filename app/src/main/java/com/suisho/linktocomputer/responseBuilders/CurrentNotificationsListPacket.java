package com.suisho.linktocomputer.responseBuilders;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.service.NotificationListenerService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CurrentNotificationsListPacket {
    private final Logger logger = LoggerFactory.getLogger(CurrentNotificationsListPacket.class);

    public JsonObject build(String requestId,StatusBarNotification[] notifications, NotificationListenerService listenerService,boolean hasPermission){
        boolean isBreak=false;
        JsonObject jsonObject=new JsonObject();
        JsonArray notificationList=new JsonArray();
        jsonObject.addProperty("_isResponsePacket",true);
        jsonObject.addProperty("_responseId",requestId);
        if(!GlobalVariables.computerConfigManager.isTrustedComputer()) {
            jsonObject.addProperty("code",1);
            logger.debug("Created active notification list packet with untrusted computer");
            isBreak=true;
        }else if(!hasPermission){
            jsonObject.addProperty("code",2);
            logger.debug("Created active notification list packet with not permission");
            isBreak=true;
        }else if(!GlobalVariables.preferences.getBoolean("function_notification_forward",false)){
            jsonObject.addProperty("code",3);
            logger.debug("Created active notification list packet with function disabled");
            isBreak=true;
        }
        if(isBreak){
            jsonObject.add("list",notificationList);
            return jsonObject;
        }
        for(StatusBarNotification statusBarNotification : notifications) {
            Notification notification=statusBarNotification.getNotification();
            final String notificationTitle=notification.extras.getString(Notification.EXTRA_TITLE);
            final String notificationContent=notification.extras.getString(Notification.EXTRA_TEXT);
            final int notificationProgress=notification.extras.getInt(Notification.EXTRA_PROGRESS,-1);
            //跳过没有内容的通知 可能用了自定义view 反正传过去没法正常显示的
            if(notificationTitle==null&&notificationContent==null){
                logger.debug("Skip notification without content");
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
            notificationJsonObject.addProperty("progress",notificationProgress);
            notificationList.add(notificationJsonObject);
        }
        jsonObject.addProperty("code",0);
        jsonObject.add("list",notificationList);
        logger.debug("Created active notification list packet.length:{}",notificationList.size());
        return jsonObject;
    }
}
