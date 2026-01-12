package com.example.linktocomputer.network;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import com.example.linktocomputer.activity.NewMainActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkStateCallback extends ConnectivityManager.NetworkCallback {
    private final NewMainActivity activity;
    private final Logger logger = LoggerFactory.getLogger(NetworkStateCallback.class);


    public NetworkStateCallback(NewMainActivity activity) {
        this.activity = activity;
    }
    @Override
    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        if(!NetworkUtil.checkNetworkUsable(activity)){
            logger.info("Network change to not wifi.Close auto connector");
            //关闭自动连接
            if(!this.activity.isDestroyed()){
                activity.runOnUiThread(()->{
                    if(activity.autoConnector != null) {
                        this.activity.autoConnector.changeViewState();
                        this.activity.autoConnector.stopListener();
                        this.activity.unregisterNetworkCallback();
                        this.activity.autoConnector=null;
                    }
                });
            }
        }
    }
}
