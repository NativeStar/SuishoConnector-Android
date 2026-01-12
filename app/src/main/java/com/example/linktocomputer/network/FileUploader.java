package com.example.linktocomputer.network;

import static java.lang.Thread.sleep;

import com.example.linktocomputer.GlobalVariables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class FileUploader {
    private Socket socket;
    private final int port;
    private final File uploadFile;
    private FileUploadEventListener eventListener;
    private FileInputStream fileInputStream;
    private OutputStream socketOutputStream;
    private byte progressUpdateCount=0;
    private long uploadedSize=0;
    private final Logger logger = LoggerFactory.getLogger(FileUploader.class);


    public FileUploader(int port, File file) {
        this.port = port;
        this.uploadFile = file;
    }

    public void start() {
        new Thread(() -> {
            //互传上传文件改改就是了...
            //好像哪里不太对劲
            try {
                this.fileInputStream = new FileInputStream(uploadFile);
                this.socket = new Socket(GlobalVariables.serverAddress, port);
                InputStream socketInputStream = socket.getInputStream();
                this.socketOutputStream = socket.getOutputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socketInputStream));
                socketOutputStream.write(GlobalVariables.computerConfigManager.getSessionId().getBytes());
                String msg = bufferedReader.readLine();
                if(msg != null) {
                    if(!msg.equals("START")) {
                        logger.warn("Computer rejected request");
                        eventListener.onError(new IOException("PC端拒绝接收请求"));
                        return;
                    }
                } else {
                    logger.warn("Computer response invalid");
                    eventListener.onError(new IOException("PC端响应无效"));
                    if(socket.isConnected()) {
                        socket.close();
                    }
                }
                eventListener.onStart();
                //正式上传数据
                while (true) {
                    //单数据包最大512字节
                    //这里真不能偷 不然文件末尾会多一堆莫名其妙的0x00
                    int availLength = Math.min(fileInputStream.available(), 512);
                    byte[] buffer = new byte[availLength];
                    if (fileInputStream.read(buffer) <= 0) {
                        socketOutputStream.write(buffer);
                        //看能不能解决漏数据
                        sleep(300);
                        socketOutputStream.close();
                        break;
                    }
                    socketOutputStream.write(buffer);
                    uploadedSize+=buffer.length;
                    //性能优化 只有更新达到次数才执行回调
                    if (progressUpdateCount == 16){
                        eventListener.onProgress(uploadedSize);
                        progressUpdateCount=0;
                    }else{
                        progressUpdateCount++;
                    }
                }
                sleep(700);
                fileInputStream.close();
                bufferedReader.close();
                socketInputStream.close();
                socket.close();
                eventListener.onSuccess(uploadFile);
                logger.debug("File '{}' upload success",uploadFile.getName());
            } catch (IOException | InterruptedException e) {
                logger.error("File '{}' upload failed with exception",uploadFile.getName(),e);
                //是否设置了回调
                if(eventListener != null) {
                    eventListener.onError(e);
                }
            }

        }).start();
    }

    public void setEventListener(FileUploadEventListener listener) {
        this.eventListener = listener;
    }

    //回调接口
    public interface FileUploadEventListener {
        void onSuccess(File file);

        void onProgress(long progress);

        void onError(Exception e);

        void onStart();
    }
}
