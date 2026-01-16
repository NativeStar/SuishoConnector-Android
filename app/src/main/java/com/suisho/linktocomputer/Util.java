package com.suisho.linktocomputer;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;

import androidx.annotation.Nullable;

import com.suisho.linktocomputer.activity.NewMainActivity;
import com.suisho.linktocomputer.constant.States;
import com.suisho.linktocomputer.instances.ComputerConfigManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Util {
    //是否打完图标包 上传时检查
    public static volatile boolean isIconPacked=false;
    private static final Logger logger = LoggerFactory.getLogger(Util.class);


    /**
     * 将文件大小转换为方便阅读的字符串
     *
     * @param size 文件字节数
     * @return 转换后的字符串
     */
    public static String coverFileSize(long size) {
        logger.debug("Origin file size data: {}", size);
        //byte
        if(size < 1024) {
            return size + "B";
        }
        //kb
        double sizeKb = (double) size / 1024;
        if(sizeKb < 1024) {
            return String.format("%.2f", sizeKb) + "KB";
        }
        //mb
        double sizeMb = sizeKb / 1024;
        if(sizeMb < 1024) {
            return String.format("%.2f", sizeMb) + "MB";
        }
        //gb
        double sizeGb = sizeMb / 1024;
        return String.format("%.2f", sizeGb) + "GB";
    }

    ;

    /**
     * 检查通知权限
     *
     * @return 是否有权限
     */
    public static boolean checkNotificationPermission(NotificationManager notificationManager) {
        return notificationManager.areNotificationsEnabled();
    }

    public static boolean checkComponentEnable(Context context, Class<?> clazz, boolean autoEnable) {
        ComponentName componentName = new ComponentName(context, clazz);
        boolean result = context.getPackageManager().getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        //自动启用
        if(!result && autoEnable) {
            logger.info("Auto enable component: {}", clazz.getName());
            context.getPackageManager().setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        return result;
    }

    public static void buildAppListCache(Activity activity) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R&&activity.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_DENIED) {
            logger.info("Not query all packages permission");
            ((NewMainActivity)activity).stateBarManager.addState(States.getStateList().get("warn_query_package_permission"));
            return;
        }
        new Thread(() -> {
            //<包名,应用名>
            HashMap<String, String> appMap = new HashMap<>();
            SharedPreferences vals = activity.getSharedPreferences("iconPackVars", Context.MODE_PRIVATE);
            PackageManager pm = activity.getPackageManager();
            List<ApplicationInfo> allPackage = pm.getInstalledApplications(0);
            //根据应用列表长度判断 上面可能不返回拒绝
            if(allPackage.size()<= 10){
                //正常不可能少于10个软件
                logger.info("Application count too low,Maybe not query all package permission");
                ((NewMainActivity)activity).stateBarManager.addState(States.getStateList().get("warn_query_package_permission"));
                return;
            }
            //应用名映射和缓存图标文件
            File appListMapObjectFile = new File(activity.getCacheDir() + "/appPackageMapper.hm");
            File iconZipFile=new File(activity.getCacheDir()+"/packing");
            //构建hashmap
            for(ApplicationInfo app : allPackage) {
                appMap.put(app.packageName, (String) app.loadLabel(pm));
            }
            //是否有打包未完成的文件 以及映射文件是否存在
            if(!iconZipFile.exists()&&appListMapObjectFile.exists()) {
                try (ObjectInputStream appListMapperInput = new ObjectInputStream(Files.newInputStream(appListMapObjectFile.toPath()));) {
                    //读取缓存的映射表
                    HashMap<String, String> mapperFileData = (HashMap<String, String>) appListMapperInput.readObject();
                    logger.debug("Loading cached object data");
                    File zipFile = new File(activity.getCacheDir() + "/tmpAppIcons");
                    //程序表是否有更改
                    if(mapperFileData.hashCode()==appMap.hashCode()&&zipFile.exists()) {
                        //两个一样 直接用缓存
                        logger.info("App list cache file comparison pass");
                        GlobalVariables.appPackageNameMapper = mapperFileData;
                        isIconPacked=true;
                        allPackage.clear();
                        System.gc();
                    } else {
                        //重构
                        logger.info("Rebuild app list cache file");
                        try (ObjectOutputStream mapperFileOutStream = new ObjectOutputStream(Files.newOutputStream(appListMapObjectFile.toPath()))) {
                            GlobalVariables.appPackageNameMapper = appMap;
                            mapperFileOutStream.writeObject(appMap);
                            mapperFileOutStream.flush();
                            mapperFileOutStream.close();
                            logger.debug("App list map cache file update success");
                            createAllPackageIconCache(activity, appMap,vals);
                        } catch (IOException e) {
                            logger.error("Error on update app list map cache file",e);
                            ((NewMainActivity)activity).stateBarManager.addState(States.getStateList().get("error_packing_icon"));
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    logger.error("Error on packing app icon pack",e);
                    //文件可能损坏
                    appListMapObjectFile.delete();
                    ((NewMainActivity)activity).stateBarManager.addState(States.getStateList().get("error_packing_icon"));
                }
            } else {
                //写入
                try (ObjectOutputStream mapperFileOutStream = new ObjectOutputStream(Files.newOutputStream(appListMapObjectFile.toPath()))) {
                    mapperFileOutStream.writeObject(appMap);
                    mapperFileOutStream.flush();
                    GlobalVariables.appPackageNameMapper = appMap;
                    logger.debug("App list map cache file create success");
                    createAllPackageIconCache(activity, appMap,vals);
                } catch (IOException e) {
                    logger.error("Error on create app list map cache file",e);
                    ((NewMainActivity)activity).stateBarManager.addState(States.getStateList().get("error_packing_icon"));
                }
            }
        }).start();
    }

    /**
     * 构建应用图标缓存
     * @param appMap 应用包名+应用名hashmap
     */
    public static void createAllPackageIconCache(Context context, @Nullable HashMap<String, String> appMap,SharedPreferences prefs) {
        new Thread(() -> {
            HashMap<String, String> appsList = appMap == null ? GlobalVariables.appPackageNameMapper : appMap;
            PackageManager pm = context.getPackageManager();
            try {
                ((NewMainActivity)context).stateBarManager.addState(States.getStateList().get("busy_packing_icon"));
                //保存文件压缩状态
                prefs.edit().putBoolean("iconPackingSucceeded", false).apply();
                File oldFile = new File(context.getCacheDir() + "/tmpAppIcons");
                //通过是否重命名判断是否正常完成打包
                File zipFile = new File(context.getCacheDir() + "/packing");
                //移除旧文件
                zipFile.delete();
                oldFile.delete();
                //打包
                logger.debug("Start packing app icon");
                ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()));
                for(String packageName : appsList.keySet()) {
                    Bitmap iconBitmap;
                    try {
                        Drawable iconDrawable = pm.getPackageInfo(packageName, 0).applicationInfo.loadIcon(pm);
                       //https://www.jianshu.com/p/1d11522ed35e
                        //懒得自己折腾 麻烦
                        int width = iconDrawable.getIntrinsicWidth();
                        int height = iconDrawable.getIntrinsicHeight();
                        iconDrawable.setBounds(0, 0, Math.min(width, 128), Math.min(height, 128));
                        Bitmap.Config config = iconDrawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                                : Bitmap.Config.RGB_565;
                        iconBitmap = Bitmap.createBitmap(Math.min(width, 128), Math.min(height, 128), config);
                        Canvas canvas = new Canvas(iconBitmap);
                        // 将drawable 内容画到画布中
                        iconDrawable.draw(canvas);
                    } catch (PackageManager.NameNotFoundException e) {
                        logger.error("Can not find package",e);
                        continue;
                    }
                    //直接打进zip
                    zipOutputStream.putNextEntry(new ZipEntry(packageName));
                    iconBitmap.compress(Bitmap.CompressFormat.PNG, 80, zipOutputStream);
                    zipOutputStream.closeEntry();
                    iconBitmap.recycle();
                }
                zipOutputStream.flush();
                zipOutputStream.close();
                //重命名
                zipFile.renameTo(oldFile);
                prefs.edit().putLong("lastUpdateTime", System.currentTimeMillis()).putBoolean("iconPackingSucceeded", true).apply();
                logger.info("Packed all icons");
                isIconPacked=true;
                if(ComputerConfigManager.needSendIconPack){
                    logger.info("Pack success.Request send icon pack");
                    GlobalVariables.computerConfigManager.sendIconPack();
                }
                ((NewMainActivity)context).stateBarManager.removeState("busy_packing_icon");
                System.gc();
            } catch (IOException e) {
                ((NewMainActivity)context).stateBarManager.removeState("busy_packing_icon");
                logger.error("Error on packing icon",e);
                ((NewMainActivity)context).stateBarManager.addState(States.getStateList().get("error_packing_icon"));
            }

        }).start();
    }
    @Nullable
    public static String calculateSHA256(File file) {
        if(!file.exists()) return null;
        logger.debug("Calculate SHA256 for file '{}'",file.getPath());
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        } catch (IOException ioe) {
            return null;
        }
        byte[] bytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    public static String calculateSHA256(byte[] data) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        byte [] result=md.digest(data);
        StringBuilder sb = new StringBuilder();
        for(byte b : result) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    public static int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
    }
    //还原ncr编码
    public static String unescape(String src){
        Pattern pattern = Pattern.compile("&#.*?;");
        Matcher matcher = pattern.matcher(src);
        while (matcher.find()){
            String group = matcher.group();
            int codePoint = Integer.parseInt(group.replaceAll("(&#|;)", ""));
            src=src.replaceAll(group, String.valueOf((char) codePoint));
            //修复无法转换+号
            src=src.replace("+","%2b");
        }
        return src;
    }
}
