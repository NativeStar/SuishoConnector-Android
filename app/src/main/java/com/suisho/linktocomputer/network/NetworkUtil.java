package com.suisho.linktocomputer.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

public class NetworkUtil {
    public static boolean checkNetworkUsable(Context context){
        ConnectivityManager connectivityManager=context.getSystemService(ConnectivityManager.class);
        if(connectivityManager == null) {
            //无网络
            return false;
        }
        NetworkCapabilities networkCapabilities= connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        if(networkCapabilities != null) {
            //暂时只允许wifi
            return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }
}
