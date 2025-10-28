package com.example.linktocomputer.instances.transmit;

import com.example.linktocomputer.abstracts.TransmitMessageAbstract;
import com.example.linktocomputer.database.TransmitDatabaseEntity;
import com.example.linktocomputer.jsonClass.TransmitMessage;

public class TransmitMessageTypeFile extends TransmitMessageAbstract {
    public String fileName;
    public long fileSize;
    public byte messageFrom;
    public boolean isDeleted;
    public String filePath;
    public final int type=TransmitMessage.MESSAGE_TYPE_FILE;
    public TransmitMessageTypeFile(TransmitDatabaseEntity messageObj){
        timestamp=messageObj.timestamp;
        fileName=messageObj.fileName;
        fileSize= messageObj.fileSize;
        messageFrom=messageObj.messageFrom;
        isDeleted= messageObj.isDeleted;
        filePath = messageObj.filePath;
    }

    public TransmitMessageTypeFile(String fileName, long fileSize, byte messageFrom, boolean isDeleted, String path) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.messageFrom = messageFrom;
        this.isDeleted = isDeleted;
        this.filePath = path;
        this.timestamp=System.currentTimeMillis();
    }

    @Override
    public int getType() {
        return TransmitMessage.MESSAGE_TYPE_FILE;
    }

    @Override
    public TransmitDatabaseEntity toDatabaseEntity() {
        TransmitDatabaseEntity databaseEntity=new TransmitDatabaseEntity();
        databaseEntity.type=type;
        databaseEntity.timestamp=timestamp;
        databaseEntity.messageFrom=messageFrom;
        databaseEntity.fileName=fileName;
        databaseEntity.fileSize=fileSize;
        databaseEntity.isDeleted=isDeleted;
        databaseEntity.filePath=filePath;
        return databaseEntity;
    }
}
