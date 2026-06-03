package com.suisho.linktocomputer.service;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.suisho.linktocomputer.GlobalVariables;
import com.suisho.linktocomputer.constant.FileManagerResultCode;
import com.suisho.linktocomputer.interfaces.IConnectedActivityMethods;
import com.suisho.linktocomputer.jsonClass.MainServiceJson;
import com.suisho.linktocomputer.network.FileServer;
import com.suisho.linktocomputer.network.TransmitDownloadFile;
import com.suisho.linktocomputer.network.FileSyncDownloader;
import com.suisho.linktocomputer.responseBuilders.EmptyResponsePacketBuilder;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.WebSocket;

public class ConnectionPacketHandle {
    public static void onTransmitUploadFilePacket(ConnectMainService service, WebSocket webSocketClient, MainServiceJson jsonObj, Logger logger, IConnectedActivityMethods activityMethods) {
        new Thread(() -> {
            //互传文件
            File transmitTargetFile;
            logger.debug("Transmit request download file");
            //Download目录下
            if(GlobalVariables.preferences.getInt("file_save_location", 0) == 1) {
                logger.debug("Save to public transmit file path");
                transmitTargetFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/Transmit");
            } else {
                //私有目录
                logger.debug("Save to private transmit file path");
                transmitTargetFile = new File(service.getExternalFilesDir(null).getAbsolutePath() + "/transmit");
            }
            transmitTargetFile.mkdirs();
            File transmitOutputFile = new File(transmitTargetFile.getAbsolutePath() + "/" + jsonObj.fileName);
            logger.debug("Create transmit output file: {}", transmitOutputFile.getAbsolutePath());
            if(transmitOutputFile.exists()) {
                String filePath;
                if(GlobalVariables.preferences.getInt("file_name_conflict_behavior", 0) == 0) {
                    //追加时间戳
                    filePath = transmitTargetFile.getAbsolutePath() + "/" + System.currentTimeMillis() + jsonObj.fileName;
                } else {
                    //删除旧文件
                    transmitOutputFile.delete();
                    filePath = transmitTargetFile.getAbsolutePath() + "/" + jsonObj.fileName;
                }
                //重名 末尾加时间戳保存
                //不能影响显示
                logger.debug("Transmit file exists, append timestamp to new file name");
                new TransmitDownloadFile(jsonObj.port, filePath, jsonObj.fileName, jsonObj.fileSize, activityMethods);
            } else {
                //没重名 一切正常
                new TransmitDownloadFile(jsonObj.port, transmitOutputFile.getAbsolutePath(), jsonObj.fileName, jsonObj.fileSize, activityMethods);
            }
            webSocketClient.send(EmptyResponsePacketBuilder.buildEmptyResponsePacket(jsonObj).toString());
        }).start();
    }

    public static void onDownloadSyncFilePacket(Logger logger, MainServiceJson jsonObj, IConnectedActivityMethods activityMethods, ConnectMainService service, WebSocket webSocketClient) {
        logger.debug("Request download sync file");
        File syncTargetFile;
        if(GlobalVariables.preferences.getInt("file_save_location", 0) == 1) {
            logger.debug("Save to public file sync path");
            syncTargetFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DOWNLOADS + "/SuishoConnector/FileSync");
        } else {
            //私有目录
            logger.debug("Save to private file sync path");
            syncTargetFile = new File(service.getExternalFilesDir(null).getAbsolutePath() + "/FileSync");
        }
        syncTargetFile.mkdirs();
        File fileSyncOutputFile = new File(syncTargetFile.getAbsolutePath() + "/" + jsonObj.fileName);
        logger.debug("Create file sync output file: {}", fileSyncOutputFile.getAbsolutePath());
        if(fileSyncOutputFile.exists()) {
            //重名 末尾加时间戳保存
            //不能影响显示
            logger.debug("Sync file exists, append timestamp to new file name");
            new FileSyncDownloader(jsonObj.port, syncTargetFile.getAbsolutePath() + "/" + System.currentTimeMillis() + jsonObj.fileName, jsonObj.fileName, jsonObj.fileSize, activityMethods);
        } else {
            //没重名 一切正常
            new FileSyncDownloader(jsonObj.port, fileSyncOutputFile.getAbsolutePath(), jsonObj.fileName, jsonObj.fileSize, activityMethods);
        }
        webSocketClient.send(EmptyResponsePacketBuilder.buildEmptyResponsePacket(jsonObj).toString());
    }

    public static void onBindDevicePacket(Logger logger, MainServiceJson jsonObj, ConnectMainService service, WebSocket webSocketClient) {
        //绑定计算机
        logger.debug("Received bind device packet");
        new Thread(() -> {
            File keyFile = new File(service.getFilesDir() + "/bind.key");
            if(keyFile.exists()) {
                logger.debug("Has exists bind key file.Delete it");
                keyFile.delete();
            }
            JsonObject response = new JsonObject();
            response.addProperty("_isResponsePacket", true);
            response.addProperty("_responseId", jsonObj._request_id);
            try (FileOutputStream keyFileOut = new FileOutputStream(keyFile)) {
                keyFileOut.write(jsonObj.msg.getBytes());
                keyFileOut.flush();
                GlobalVariables.settings.edit().putBoolean("boundDevice", true).apply();
                response.addProperty("success", true);
                logger.info("Bind computer success");
            } catch (IOException e) {
                logger.error("Error when write bind key file", e);
                response.addProperty("success", false);
            } finally {
                webSocketClient.send(response.toString());
            }
        }).start();
    }

    public static void onCheckPermissionPacket(Logger logger, MainServiceJson jsonObj, ConnectMainService service, WebSocket webSocketClient) {
        logger.debug("Received check permission packet");
        JsonObject permissionCheckPacket = new JsonObject();
        permissionCheckPacket.addProperty("_isResponsePacket", true);
        permissionCheckPacket.addProperty("_responseId", jsonObj._request_id);
        //存储空间权限
        if(jsonObj.name.equals("android.permission.MANAGE_EXTERNAL_STORAGE")) {
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                logger.debug("Check storage permission on android 10 and newer");
                permissionCheckPacket.addProperty("result", Environment.isExternalStorageManager());
            } else {
                logger.debug("Check storage permission on android 9 and lower");
                boolean hasPermission = service.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED && service.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
                permissionCheckPacket.addProperty("result", hasPermission);
            }
            webSocketClient.send(permissionCheckPacket.toString());
            return;
        }
        webSocketClient.send(permissionCheckPacket.toString());
    }

    public static void onGetFileListPacket(Logger logger, MainServiceJson jsonObj, FileServer webFileServer, WebSocket webSocketClient,ConnectMainService service) {
        new Thread(() -> {
            JsonObject fileListPacket = new JsonObject();
            fileListPacket.addProperty("_isResponsePacket", true);
            fileListPacket.addProperty("_responseId", jsonObj._request_id);
            //功能是否开启及设备是否被信任
            if(!GlobalVariables.preferences.getBoolean("function_file_manager", false) || !GlobalVariables.computerConfigManager.isTrustedComputer()) {
                fileListPacket.addProperty("code", GlobalVariables.computerConfigManager.isTrustedComputer() ? FileManagerResultCode.CODE_FUNCTION_DISABLED : FileManagerResultCode.CODE_DEVICE_NOT_TRUSTED);
                webSocketClient.send(fileListPacket.toString());
                return;
            }
            //读文件
            File dir = new File(jsonObj.msg);
            if(!dir.isDirectory()) {
                //不是目录
                logger.debug("Access path '{}' not directory", dir.getPath());
                fileListPacket.addProperty("code", FileManagerResultCode.CODE_NOT_DIR);
                webSocketClient.send(fileListPacket.toString());
                return;
            }
            File[] files = dir.listFiles();
            if(files == null) {
                //无法列出文件
                logger.debug("Access path '{}' not permission", dir.getPath());
                fileListPacket.addProperty("code", FileManagerResultCode.CODE_NOT_PERMISSION);
                webSocketClient.send(fileListPacket.toString());
                return;
            }
            JsonArray fileListJsonArray = new JsonArray();
            for(File inDirectoryFile : files) {
                JsonObject fileInfoObject = new JsonObject();
                fileInfoObject.addProperty("type", inDirectoryFile.isDirectory() ? "folder" : "file");
                fileInfoObject.addProperty("name", inDirectoryFile.getName());
                fileInfoObject.addProperty("size", inDirectoryFile.length());
                fileListJsonArray.add(fileInfoObject);
            }
            fileListPacket.addProperty("code", FileManagerResultCode.CODE_NORMAL);
            fileListPacket.add("files", fileListJsonArray);
            webSocketClient.send(fileListPacket.toString());
            //已经访问了目录 提前开启服务器
            logger.debug("Prestart file server for access file");
            if(!webFileServer.wasStarted()) {
                try {
                    webFileServer.start();
                } catch (IOException e) {
                    logger.error("Error when start file server", e);
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("packetType", "edit_state");
                    jsonObject.addProperty("type", "add");
                    jsonObject.addProperty("name", "error_phone_file_server");
                    service.sendObject(jsonObject);
                }
            }
        }).start();
    }
}
