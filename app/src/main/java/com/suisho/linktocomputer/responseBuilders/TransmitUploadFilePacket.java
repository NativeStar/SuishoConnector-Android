package com.suisho.linktocomputer.responseBuilders;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.suisho.linktocomputer.instances.EncryptionKey;

public class TransmitUploadFilePacket {
    JsonObject jsonObject;
    public TransmitUploadFilePacket(String fileName, long fileSize, EncryptionKey encryptionKey) {
        jsonObject = new JsonObject();
        jsonObject.addProperty("packetType", "action_transmit");
        jsonObject.addProperty("messageType", "file");
        /*文件名*/
        jsonObject.addProperty("name", fileName);
        /*大小*/
        jsonObject.addProperty("size", fileSize);
        //密钥
        jsonObject.addProperty("encryptKey", encryptionKey.getKeyBase64());
        //向量
        jsonObject.addProperty("encryptIv", encryptionKey.getIvBase64());
    }

    @NonNull
    @Override
    public String toString() {
        return this.jsonObject.toString();
    }
    public JsonObject getJsonObject(){
        return jsonObject;
    }
}
