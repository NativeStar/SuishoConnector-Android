package com.example.linktocomputer.abstracts;

import com.example.linktocomputer.database.TransmitDatabaseEntity;

public abstract class TransmitMessageAbstract {
//    byte test=1;
    //获取类型
    public int getType() {
        return -1;
    }
    public long timestamp;
    public abstract TransmitDatabaseEntity toDatabaseEntity();
}
