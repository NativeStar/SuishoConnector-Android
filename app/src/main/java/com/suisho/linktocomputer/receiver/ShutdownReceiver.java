package com.suisho.linktocomputer.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.suisho.linktocomputer.enums.ConnectionCloseCode;
import com.suisho.linktocomputer.interfaces.IBroadcastReceiver;
import com.suisho.linktocomputer.service.ConnectMainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownReceiver extends BroadcastReceiver implements IBroadcastReceiver {
    private final ConnectMainService networkService;
    private final Logger logger = LoggerFactory.getLogger(ShutdownReceiver.class);

    public ShutdownReceiver(ConnectMainService service) {
        this.networkService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logger.debug("Device will shutdown");
        if(networkService.isConnected) {
            logger.info("Device will shutdown,disconnecting");
            networkService.disconnect(ConnectionCloseCode.PhoneWillShutdown, null);
        }
    }

    public static IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        return filter;
    }
}
