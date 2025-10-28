package com.example.linktocomputer.responseBuilders;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

public class NotificationPacket {
    private final JsonObject jsonObject;
    public NotificationPacket(String pkg,long time, String title, @Nullable String content,String appName,String key,boolean ongoing) {
        jsonObject=new JsonObject();
        jsonObject.addProperty("packetType","action_notificationForward");
        //包名
        jsonObject.addProperty("package",pkg);
        //应用名
        jsonObject.addProperty("appName",appName);
        //发送时间
        jsonObject.addProperty("time",time);
        //暂时这样
        //标题
        jsonObject.addProperty("title",title);
        //内容
        jsonObject.addProperty("content",content);
        //唯一键
        jsonObject.addProperty("key",key);
        //是否常驻
        jsonObject.addProperty("ongoing",ongoing);
    }
    public JsonObject getJsonObject(){
        return jsonObject;
    }
}
