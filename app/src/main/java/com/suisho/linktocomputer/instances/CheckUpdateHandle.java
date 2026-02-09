package com.suisho.linktocomputer.instances;

import android.os.MessageQueue;

import com.suisho.linktocomputer.BuildConfig;
import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.activity.NewMainActivity;
import com.suisho.linktocomputer.constant.States;
import com.suisho.linktocomputer.jsonClass.CheckUpdateJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CheckUpdateHandle implements MessageQueue.IdleHandler {
    private final Logger logger = LoggerFactory.getLogger(CheckUpdateHandle.class);
    private final NewMainActivity activity;

    public CheckUpdateHandle(NewMainActivity mainActivity) {
        this.activity = mainActivity;
    }

    @Override
    public boolean queueIdle() {
        logger.debug("Handle called.Checking update");
        new Thread(()->{
            Request request = new Request.Builder()
                    .method("GET",null)
                    .url("https://raw.githubusercontent.com/NativeStar/SuishoConnector-Android/master/update.json")
                    .cacheControl(new CacheControl.Builder().noCache().build())
                    .build();
            try (Response response = new OkHttpClient().newCall(request).execute()){
                if(response.isSuccessful()){
                    String rawJsonString = response.body().string();
                    CheckUpdateJson jsonInstance= GlobalVariables.jsonBuilder.fromJson(rawJsonString, CheckUpdateJson.class);
                    if(BuildConfig.VERSION_CODE < jsonInstance.versionCode){
                        logger.info("Update available:{}", jsonInstance.versionName);
                        activity.stateBarManager.addState(States.getStateList().get("info_update_available"));
                        return;
                    }
                    logger.info("Current is latest version:{}", jsonInstance.versionName);
                }else{
                    logger.warn("Check update failed with response code {}", response.code());
                }
            } catch (Exception e) {
                logger.error("Failed to check update",e);
                /*暂时不显示给用户*/
            }
        }).start();
        return false;
    }
}
