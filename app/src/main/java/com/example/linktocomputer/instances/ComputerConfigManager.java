package com.example.linktocomputer.instances;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.abstracts.RequestHandle;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.instances.adapter.TrustedDeviceListAdapter;
import com.example.linktocomputer.jsonClass.MainServiceJson;
import com.example.linktocomputer.network.FileUploader;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ComputerConfigManager {
    private final String name;

    public String getId() {
        return id;
    }

    private final String id;
    private final SharedPreferences config;
    private final SharedPreferences historyComputerList;
    private final boolean firstConnect;
    private final String sessionId;
    //避免频繁读取sharedpref
    private boolean trusted;
    //如果请求上传时文件未打包完成将此设为true 打完包会检查
    public static boolean needSendIconPack = false;
    private final NewMainActivity context;
    private final ConnectMainService networkService;
    //本次运行是否与该设备同步过(发起上传就算)
    private boolean synced = false;
    private final Logger logger = LoggerFactory.getLogger(ComputerConfigManager.class);

    public ComputerConfigManager(String name, String id, NewMainActivity context, ConnectMainService networkService,String sessionId) {
        this.name = name;
        this.id = id;
        this.context = context;
        this.sessionId=sessionId;
        this.config = context.getSharedPreferences(this.id, Context.MODE_PRIVATE);
        this.historyComputerList = context.getSharedPreferences("computerHistory", Context.MODE_PRIVATE);
        this.firstConnect = config.getBoolean("isFirstConnect", true);
        //首次连接标记
        this.trusted = config.getBoolean("trustedDevice", false);
        //名称和连接时间
        config.edit().putString("name", name).putLong("lastConnectTime", System.currentTimeMillis()).apply();
        if(firstConnect) config.edit().putBoolean("isFirstConnect", false).apply();
        //所有连接过的计算机列表
        Set<String> computerListSet = historyComputerList.getStringSet("list", new HashSet<>());
        //离谱:
        //Do not modify the set returned by SharedPreferences.getStringSet()
        HashSet<String> clonedComputerListSet = new HashSet<>(computerListSet);
        clonedComputerListSet.add(this.id);
        historyComputerList.edit().putStringSet("list", clonedComputerListSet).apply();
        this.networkService = networkService;
    }

    public void init(@Nullable Runnable onSuccess) {
        new Thread(() -> {
            //发起同步图标包请求
            sendIconPack();
            if(onSuccess != null) onSuccess.run();
        }).start();
    }

    /**
     * 获取设备列表
     *
     * @param context 拉取sharedPref用
     * @return 设备列表
     */
    public static ArrayList<TrustedDeviceListAdapter.DeviceTrustInstance> getAllComputers(Context context) {
        ArrayList<TrustedDeviceListAdapter.DeviceTrustInstance> list = new ArrayList<>();
        Set<String> rawComputerList = context.getSharedPreferences("computerHistory", Context.MODE_PRIVATE).getStringSet("list", new HashSet<>());
        rawComputerList.forEach(id -> {
            SharedPreferences computerInfo = context.getSharedPreferences(id, Context.MODE_PRIVATE);
            if(!computerInfo.getBoolean("trustedDevice", false)) return;
            TrustedDeviceListAdapter.DeviceTrustInstance deviceTrustInstance = new TrustedDeviceListAdapter.DeviceTrustInstance(
                    computerInfo.getString("name", "unknown"),
                    id,
                    computerInfo.getLong("lastConnectTime", 0L)
            );
            list.add(deviceTrustInstance);
        });
        return list;
    }

    public void sendIconPack() {
        logger.info("Request send icon pack");
        if(synced) return;
        SharedPreferences iconPackVals = context.getSharedPreferences("iconPackVars", Context.MODE_PRIVATE);
        if(!Util.isIconPacked) {
            logger.info("Waiting packing icon pack");
            needSendIconPack = true;
            //未完成打包 等打完会调用请求发送的 不急
            return;
        }
        File iconPackFile = new File(context.getCacheDir() + "/tmpAppIcons");
        JsonObject packet = new JsonObject();
        packet.addProperty("packetType", "syncIconPack");
        packet.addProperty("lastUpdateTime", iconPackVals.getLong("lastUpdateTime", 1L));
        packet.addProperty("hash", Util.calculateSHA256(iconPackFile));
        logger.debug("Send icon pack sync request");
        networkService.sendRequestPacket(packet, new RequestHandle() {
            @Override
            public void run(String data) {
                MainServiceJson jsonObj = GlobalVariables.jsonBuilder.fromJson(data, MainServiceJson.class);
                if(jsonObj._result.equals("ERROR")) {
                    logger.info("Target computer rejected upload request:{}",jsonObj.msg);
                    return;
                }
                synced = true;
                FileUploader fileUploader = new FileUploader(jsonObj.port, iconPackFile);
                fileUploader.setEventListener(new FileUploader.FileUploadEventListener() {
                    @Override
                    public void onSuccess(File file) {
                        logger.info("Upload icon pack success");
                        needSendIconPack = false;
                    }

                    @Override
                    public void onProgress(long progress) {

                    }

                    @Override
                    public void onError(Exception e) {

                    }

                    @Override
                    public void onStart() {

                    }
                });
                fileUploader.start();
            }
        });
    }

    public ConnectMainService getNetworkService() {
        return networkService;
    }

    public boolean isFirstConnect() {
        return firstConnect;
    }

    public boolean isTrustedComputer() {
        return trusted;
    }

    public void setTrusted(boolean trust) {
        logger.info("Set trusted:{}",trust);
        this.trusted = trust;
        config.edit().putBoolean("trustedDevice", trust).apply();
        //通知服务端
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("packetType", "trustModeChange");
        jsonObject.addProperty("trusted", trusted);
        context.sendPacket(jsonObject);
    }
    public String getSessionId(){
        return sessionId;
    }
    //给切换用 方便点
    public void changeTrustMode() {
        setTrusted(!trusted);
    }
}
