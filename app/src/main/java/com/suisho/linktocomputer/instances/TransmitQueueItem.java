package com.suisho.linktocomputer.instances;

import com.google.gson.JsonObject;

import java.io.InputStream;

public class TransmitQueueItem {
    public InputStream fileInputStream;
    public JsonObject requestPacket;
    public long fileSize;
    public EncryptionKey encryptionKey;
    public String fileName;

    public TransmitQueueItem(InputStream stream, JsonObject requestPacket, long fileSize, EncryptionKey encryptionKey, String fileName) {
        this.fileInputStream = stream;
        this.requestPacket = requestPacket;
        this.fileSize = fileSize;
        this.encryptionKey = encryptionKey;
        this.fileName = fileName;
    }
}
