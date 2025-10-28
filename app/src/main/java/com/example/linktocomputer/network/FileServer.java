package com.example.linktocomputer.network;

import android.util.Log;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.Util;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.constant.States;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;

import fi.iki.elonen.NanoHTTPD;

public class FileServer extends NanoHTTPD {
    private boolean isInitialized = false;
    //    private final String[] videoFileExt=new String[]{"mp4","webm"};
    private String lastRequestVideoFilePath = "";
    private String sessionId = "";
    private RandomAccessFile videoFileRandomAccess;

    public FileServer(int port) {
        super(port);
    }

    public void init(InputStream keyInputStream, String sessionId, NewMainActivity activity) {
        if(isInitialized) return;
        this.sessionId = sessionId;
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(keyInputStream, "SuishoConnectorPwd".toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "SuishoConnectorPwd".toCharArray());
            makeSecure(makeSSLSocketFactory(keyStore, keyManagerFactory), null);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException |
                 UnrecoverableKeyException e) {
            Log.e("FileServer", "Error!!!", e);
            //双平台通知
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("packetType", "edit_state");
            jsonObject.addProperty("type", "add");
            jsonObject.addProperty("name", "error_phone_file_server");
            GlobalVariables.computerConfigManager.getNetworkService().sendObject(jsonObject);
            activity.stateBarManager.addState(States.getStateList().get("error_phone_file_server"));
        } finally {
            isInitialized = true;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        if(!GlobalVariables.computerConfigManager.isTrustedComputer()) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Untrusted");
        }
        //收到任何请求都会报"Could not send response to the client" 但似乎啥实际异常都没有 不理了
        String requestCookieSessionId = session.getCookies().read("sessionId");
        //验证sessionId和ip地址
        if(requestCookieSessionId == null || !requestCookieSessionId.equals(this.sessionId) || !session.getRemoteIpAddress().equals(GlobalVariables.serverAddress)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Auth failed");
        }
        String url = session.getUri();
        if(url.endsWith("favicon.ico")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not icon");
        }
        List<String> filePathList = session.getParameters().get("filePath");
        if(filePathList != null) {
            String filePath = filePathList.get(0);
            if(filePath == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not args");
            }
            String filePathDecoded = Util.unescape(filePath);
            File targetFile = new File(filePathDecoded);
            if(!targetFile.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
            }
            try {
                String targetFileName = targetFile.getName();
                //对视频文件分段发送
                if((targetFileName.endsWith("mp4") || targetFileName.endsWith("webm")) && session.getHeaders().containsKey("range")) {
                    RangeRequestPoint fileRange = parseRangeHeader(session.getHeaders(), targetFile.length());
                    //只能同时处理一个文件
                    if(!lastRequestVideoFilePath.equals(targetFile.getPath())) {
                        lastRequestVideoFilePath = targetFile.getPath();
                        if(videoFileRandomAccess != null) {
                            videoFileRandomAccess.close();
                        }
                        videoFileRandomAccess = new RandomAccessFile(targetFile, "r");
                    }
                    byte[] buffer = new byte[(int) fileRange.size];
                    videoFileRandomAccess.seek(fileRange.start);
                    videoFileRandomAccess.readFully(buffer);
                    Response rangeResponse = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "application/octet-stream", new ByteArrayInputStream(buffer), fileRange.size);
                    rangeResponse.addHeader("Content-Range", "bytes " + fileRange.start + "-" + fileRange.end + "/" + targetFile.length());
                    return rangeResponse;
                }
                FileInputStream fileInputStream = new FileInputStream(targetFile);
                return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fileInputStream, targetFile.length());
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text.plain", e.getMessage());
            }
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not args");
        }
    }

    private RangeRequestPoint parseRangeHeader(Map<String, String> headers, long fileSize) {
        String rangeHeader = headers.get("range");
//        if (rangeHeader.startsWith("bytes=")) {
        RangeRequestPoint rangeRequestPoint = new RangeRequestPoint();
        String rangeValue = rangeHeader.substring("bytes=".length());
        String[] ranges = rangeValue.split("-");
        rangeRequestPoint.start = Long.parseLong(ranges[0]);
        if(ranges.length > 1 && !ranges[1].isEmpty()) {
            rangeRequestPoint.end = Long.parseLong(ranges[1]);
        } else {
            rangeRequestPoint.end = fileSize - 1;
        }
        rangeRequestPoint.size = rangeRequestPoint.end - rangeRequestPoint.start + 1;
        return rangeRequestPoint;
//        }
    }

    private static final class RangeRequestPoint {
        public long start;
        public long end;
        public long size;
    }
}
