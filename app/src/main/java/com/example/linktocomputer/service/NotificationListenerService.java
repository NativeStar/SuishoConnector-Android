package com.example.linktocomputer.service;

import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.instances.MediaSessionManager;
import com.example.linktocomputer.responseBuilders.NotificationPacket;
import com.google.gson.JsonObject;

import java.util.HashMap;


public class NotificationListenerService extends android.service.notification.NotificationListenerService {
    //是否已被系统调用绑定
    private boolean enable;
    public static boolean systemBound = false;
    private ConnectMainService networkService;
    private PackageManager packageManager;
    private final HashMap<String, String> appNameCache = new HashMap<>();
    private String appPackageName;
    private MediaSessionManager mediaSessionManager;

    public NotificationListenerService() {
    }

    @Override
    public StatusBarNotification[] getActiveNotifications() {
        var notificationsList = super.getActiveNotifications();
        for(var statusBarNotification : notificationsList) {
            var notification = statusBarNotification.getNotification();
            //更新媒体通知
            MediaSession.Token mediaSessionToken = notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
            if(mediaSessionToken != null) {
                onNewMediaNotification(statusBarNotification, notification, mediaSessionToken);
            }
        }
        return notificationsList;
    }

    @Override
    public IBinder onBind(Intent intent) {
        //根据请求者不同返回不同对象
        if(intent.getAction() != null && !intent.getAction().equals("networkServiceLaunch")) {
            Log.d("NotificationListener", "System bound");
            systemBound = true;
            return super.onBind(intent);
        }
        Log.d("NotificationListener", "Network Service bound");
        appPackageName = getPackageName();
        return new MyBinder();
    }

    public void setMainService(ConnectMainService service) {
        networkService = service;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    public void appendMediaSessionControl(String action,@Nullable Long seekTime){
        if(mediaSessionManager != null) {
            mediaSessionManager.appendControl(action, seekTime);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        super.onNotificationRemoved(sbn, rankingMap, reason);
        if(networkService == null) return;
        JsonObject removeNotificationPacket = new JsonObject();
        removeNotificationPacket.addProperty("packetType", "removeActiveNotification");
        removeNotificationPacket.addProperty("key", sbn.getKey());
        networkService.sendObject(removeNotificationPacket);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        //只对信任的设备推送
        if(packageManager == null) {
            packageManager = getPackageManager();
        }
        //未开启转发、未连接或设备不信任时返回
        if(!enable) return;
        if(GlobalVariables.computerConfigManager != null && !GlobalVariables.computerConfigManager.isTrustedComputer())
            return;
        super.onNotificationPosted(sbn);
        //忽略自身
        if(sbn.getPackageName().equals(appPackageName)) return;
        if(networkService != null) {
            var notificationInstance = sbn.getNotification();
            //检查是否为媒体通知
            MediaSession.Token mediaSessionToken = notificationInstance.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
            if(mediaSessionToken != null) {
                onNewMediaNotification(sbn, notificationInstance, mediaSessionToken);
            }
            //拒绝转发电子垃圾
//        Log.i("NotificationForward", sbn.getPackageName() + ":" + notificationInstance.extras.getString(Notification.EXTRA_TITLE, "EMPTY!!!") + ":" + notificationInstance.extras.getString(Notification.EXTRA_TEXT, "EMPTY!!!")+":"+notificationInstance.extras.getInt(Notification.EXTRA_PROGRESS,-1));
            if(isRubbishNotification(notificationInstance.extras.getString(Notification.EXTRA_TITLE, ""), notificationInstance.extras.getString(Notification.EXTRA_TEXT, ""), notificationInstance.extras.getInt(Notification.EXTRA_PROGRESS, -1)))
                return;
            NotificationPacket packet;
            try {
                //应用名缓存 应该比一直getPackageInfo性能好点
                if(!appNameCache.containsKey(sbn.getPackageName())) {
                    appNameCache.put(sbn.getPackageName(), packageManager.getPackageInfo(sbn.getPackageName(), 0).applicationInfo.loadLabel(packageManager).toString());
                }
                packet = new NotificationPacket(
                        sbn.getPackageName(),
                        sbn.getPostTime(),
                        notificationInstance.extras.getString(Notification.EXTRA_TITLE, ""),
                        notificationInstance.extras.getString(Notification.EXTRA_TEXT),
                        appNameCache.get(sbn.getPackageName()),
                        sbn.getKey(),
                        (notificationInstance.flags & Notification.FLAG_ONGOING_EVENT) != 0,
                        notificationInstance.extras.getInt(Notification.EXTRA_PROGRESS, -1)
                );
            } catch (PackageManager.NameNotFoundException e) {
                packet = new NotificationPacket(
                        sbn.getPackageName(),
                        sbn.getPostTime(),
                        notificationInstance.extras.getString(Notification.EXTRA_TITLE, ""),
                        notificationInstance.extras.getString(Notification.EXTRA_TEXT),
                        //暂时取消名称获取
                        sbn.getPackageName(),
                        sbn.getKey(),
                        (notificationInstance.flags & Notification.FLAG_ONGOING_EVENT) != 0,
                        notificationInstance.extras.getInt(Notification.EXTRA_PROGRESS, -1)
                );
            }
            networkService.sendObject(packet.getJsonObject());
        }
    }

    /**
     * 检测常见无意义通知 如安卓的后台运行提醒
     *
     * @return 是否应被强制过滤
     */
    private boolean isRubbishNotification(String title, @Nullable String content, int progress) {
        return (content == null || content.contains("点按即可了解详情或停止应用。") || content.isEmpty()) && progress == -1;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
    }

    @Nullable
    public String getCachedAppName(String pkgName) {
        if(packageManager == null) {
            packageManager = getPackageManager();
        }
        if(!appNameCache.containsKey(pkgName)) {
            try {
                appNameCache.put(pkgName, packageManager.getPackageInfo(pkgName, 0).applicationInfo.loadLabel(packageManager).toString());
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("main", "Failed to get app name by package name", e);
                return "ERROR!!!";
            }
        }
        return appNameCache.get(pkgName);
    }

    private void onNewMediaNotification(StatusBarNotification sbn, Notification notification, MediaSession.Token token) {
        if(mediaSessionManager == null) {
            mediaSessionManager = new MediaSessionManager(networkService, sbn, token);
        } else {
            mediaSessionManager.setNewMediaSession(sbn, token);
        }
    }

    public class MyBinder extends Binder {
        public NotificationListenerService getService() {
            return NotificationListenerService.this;
        }
    }
}