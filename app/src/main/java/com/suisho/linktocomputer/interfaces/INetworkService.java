package com.suisho.linktocomputer.interfaces;

import androidx.annotation.Nullable;

import com.suisho.linktocomputer.abstracts.FileUploadStateHandle;
import com.suisho.linktocomputer.abstracts.RequestHandle;
import com.suisho.linktocomputer.instances.EncryptionKey;
import com.google.gson.JsonObject;

import java.io.InputStream;

public interface INetworkService {
    void sendString(String data);
    void sendRequestPacket(JsonObject object, RequestHandle handle);
    void onResponsePacket(String id, String data);
    void uploadFile(InputStream stream, int port, boolean isSmallFile, @Nullable FileUploadStateHandle handle, long fileSize, EncryptionKey encryptionKey);
}
