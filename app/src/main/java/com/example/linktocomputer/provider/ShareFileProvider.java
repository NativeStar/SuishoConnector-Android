package com.example.linktocomputer.provider;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.core.content.FileProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ShareFileProvider extends FileProvider {
    static volatile String shareFileName="";
    private final Logger logger = LoggerFactory.getLogger(ShareFileProvider.class);

    public ShareFileProvider() {
    }
    public static synchronized void setShareFileName(String name){
        shareFileName=name;
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = new File(getContext().getFilesDir(), uri.getLastPathSegment());
        logger.debug("Share file path:{}",file.getAbsolutePath());
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        cursor.addRow(new Object[]{shareFileName, file.length()});
        return cursor;
    }
}