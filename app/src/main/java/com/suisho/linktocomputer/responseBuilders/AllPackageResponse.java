package com.suisho.linktocomputer.responseBuilders;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AllPackageResponse {
    private final String requestId;
    private final Context context;
    private final PackageManager pm;
    private final Logger logger = LoggerFactory.getLogger(AllPackageResponse.class);


    public AllPackageResponse(String requestId, Context context) {
        this.requestId = requestId;
        this.context = context;
        this.pm = context.getPackageManager();
    }

    public JsonObject create() {
        //缓存 免得老是遍历列表
        //现在不用了 改成pc端缓存+用户需要时手动触发刷新
        JsonObject object = new JsonObject();
        object.addProperty("_responseId", requestId);
        object.addProperty("_isResponsePacket", true);
        object.add("data", getAllPackage());
        logger.debug("Send all package packet");
        return object;
    }

    private JsonArray getAllPackage() {
        JsonArray jsonArray = new JsonArray();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_DENIED) {
            logger.info("No permission to query all packages,return empty list");
            //直接返回空数组
            return jsonArray;
        }
        List<ApplicationInfo> installedPackages = context.getPackageManager().getInstalledApplications(0);
        for(ApplicationInfo pkgInfo : installedPackages) {
            JsonObject appObj = new JsonObject();
            appObj.addProperty("packageName", pkgInfo.packageName);
            appObj.addProperty("appName", (String) pkgInfo.loadLabel(this.pm));
            jsonArray.add(appObj);
        }
        logger.debug("Created all package list,length:{}", jsonArray.size());
        return jsonArray;
    }
}
