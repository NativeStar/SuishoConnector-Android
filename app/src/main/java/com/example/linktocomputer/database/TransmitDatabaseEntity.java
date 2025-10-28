package com.example.linktocomputer.database;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class TransmitDatabaseEntity {
    public TransmitDatabaseEntity() {}
    @Id(assignable = true)
    public long timestamp;
    public byte type;
    public byte messageFrom;
    public long fileSize;
    public String msg;
    public boolean isDeleted;
    public String fileName;
    public String filePath;
}
