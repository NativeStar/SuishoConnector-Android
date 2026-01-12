package com.example.linktocomputer.responseBuilders;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.example.linktocomputer.R;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HandshakeResponse {
    private final JsonObject jsonObject=new JsonObject();
    private final Context context;
    private final Logger logger = LoggerFactory.getLogger(HandshakeResponse.class);

    public HandshakeResponse(Context context) {
        this.context=context;
    }
    public JsonObject build(){
        jsonObject.addProperty("packetType", "connect_handshake_pong");
        //时间戳
        jsonObject.addProperty("time", System.currentTimeMillis());
        //瞎搞的
        jsonObject.addProperty("msg", "GALAXY");
        //协议版本
        jsonObject.addProperty("protocolVersion", context.getResources().getInteger(R.integer.protoVersion));
        //型号
        jsonObject.addProperty("modelName", getModelName());
        //品牌
        jsonObject.addProperty("oem", Build.BRAND);
        //androidId
        //可能为null
        jsonObject.addProperty("androidId", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        //安卓版本
        jsonObject.addProperty("androidVersion", Build.VERSION.SDK_INT);
        logger.debug("Created handshake response packet");
        return jsonObject;
    }
    //获取设备型号 优先获取市场名
    //中国大陆常见品牌
    private String getModelName(){
        //中国大陆各品牌手机市场名属性
        String[] modelProps=new String[]{"ro.config.marketing_name","ro.oppo.market.enname","ro.vendor.oplus.market.enname","ro.vendor.vivo.market.name","ro.vivo.market.name","ro.vendor.oplus.market.name"
                ,"ro.product.marketname","ro.product.odm.marketname","ro.product.vendor.marketname"};
        try {
            Class<?> sysProp=Class.forName("android.os.SystemProperties");
            Method getPropMethod= sysProp.getDeclaredMethod("get",String.class, String.class);
            String modelName="";
            for(String propName:modelProps){
                modelName=(String) getPropMethod.invoke(sysProp,propName,"");
                if(modelName!=null&&!modelName.isEmpty()) break;
            }
            //过了一圈还是拿不到市场名的话
            if(modelName==null||modelName.isEmpty()){
                logger.info("Market name not found, using model name");
                return Build.MODEL;
            }
            logger.debug("Market name:{}",modelName);
            return modelName;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException e) {
            logger.error("Error getting market name",e);
            //反射炸了 备用方案
            //这个肯定获取得到
            return Build.MODEL;
        }
    }
}
