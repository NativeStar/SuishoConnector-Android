package com.example.linktocomputer.provider;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;

public class ShareFileProvider extends FileProvider {
    static volatile String shareFileName="";
    public ShareFileProvider() {
    }
    public static synchronized void setShareFileName(String name){
        shareFileName=name;
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = new File(getContext().getFilesDir(), uri.getLastPathSegment());
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        Log.i("main","Shared file name:"+shareFileName);
        cursor.addRow(new Object[]{shareFileName, file.length()});
        return cursor;
    }
}