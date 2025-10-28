package com.example.linktocomputer.instances.transmit;

import com.example.linktocomputer.abstracts.TransmitMessageAbstract;
import com.example.linktocomputer.database.TransmitDatabaseEntity;
import com.example.linktocomputer.jsonClass.TransmitMessage;

public class TransmitMessageTypeText extends TransmitMessageAbstract {
    public String msg;
    public byte messageFrom;
    //类型 留着给序列化json
    public final byte type=TransmitMessage.MESSAGE_TYPE_TEXT;
    public TransmitMessageTypeText(TransmitDatabaseEntity message) {
        msg=message.msg;
        timestamp=message.timestamp;
        messageFrom= message.messageFrom;
    }
    public TransmitMessageTypeText(String sendMsg){
        msg=sendMsg;
        timestamp=System.currentTimeMillis();
        messageFrom=TransmitMessage.MESSAGE_FROM_PHONE;
    }
    public TransmitMessageTypeText(String sendMsg,boolean formPhone){
        msg=sendMsg;
        messageFrom=formPhone?TransmitMessage.MESSAGE_FROM_PHONE:TransmitMessage.MESSAGE_FROM_COMPUTER;
        timestamp=System.currentTimeMillis();
    }
    @Override
    public TransmitDatabaseEntity toDatabaseEntity(){
        TransmitDatabaseEntity databaseEntity=new TransmitDatabaseEntity();
        databaseEntity.timestamp=timestamp;
        databaseEntity.messageFrom=messageFrom;
        databaseEntity.msg=msg;
        databaseEntity.type=type;
        return databaseEntity;
    }

    @Override
    public int getType() {
        return type;
    }
}
