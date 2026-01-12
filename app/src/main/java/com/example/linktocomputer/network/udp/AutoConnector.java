package com.example.linktocomputer.network.udp;

import android.net.wifi.WifiManager;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.constant.States;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class AutoConnector extends Thread {
    private final String keyFilePath;
    private NewMainActivity activity;
    private final WifiManager.MulticastLock lock;
    private boolean looping = true;
    private DatagramSocket socket = null;
    private final Logger logger = LoggerFactory.getLogger(AutoConnector.class);


    public AutoConnector(String keyPath, NewMainActivity act, WifiManager.MulticastLock broadcastLock) {
        this.keyFilePath = keyPath;
        this.activity = act;
        this.lock = broadcastLock;
    }

    @Override
    public void run() {
        try {
            int port = 60127;
            socket = new DatagramSocket(port);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            //key
            FileInputStream keyInput = new FileInputStream(keyFilePath);
            byte[] keyData = new byte[256];
            keyInput.read(keyData);
            keyInput.close();
            //androidId
            byte[] androidIdBuffer= GlobalVariables.androidId.getBytes();
            byte[] udpBuffer = new byte[androidIdBuffer.length];
            logger.info("Auto connector start listen");
            while (looping) {
                DatagramPacket packet = new DatagramPacket(udpBuffer, udpBuffer.length);
                socket.receive(packet);
                InetAddress senderAddress = packet.getAddress();
                if(Arrays.equals(packet.getData(), androidIdBuffer)) {
                    logger.info("Received self broadcast,start connect");
                    //按手动连接来
                    activity.runOnUiThread(() -> {
                        //前面是阻塞的 修复点击连接相关按钮后仍会自动连接
                        if(!looping) return;
                        activity.connectByAddressInput(senderAddress.toString().replace("/", ""), String.valueOf(39865),null,new String(keyData));
                        if(this.lock.isHeld()) this.lock.release();
                    });
                    break;
                }
            }
        } catch (IOException e) {
            String errorMsg=e.getMessage();
            //关闭socket时receive方法会导致这个异常 这是无害的
            if(errorMsg!=null&&errorMsg.equals("Socket closed")) return;
            logger.error("Auto connector error",e);
            activity.stateBarManager.addState(States.getStateList().get("error_auto_connect"));
            //去掉连接中提示
            activity.runOnUiThread(() -> {
                activity.viewPagerAdapter.getHomeFragment().setAutoConnecting(false);
            });
        } finally {
            if(socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * 停止监听广播
     */
    public void stopListener() {
        //释放多播锁
        logger.info("Stop auto connector listen");
        if(this.lock.isHeld()) lock.release();
        activity.unregisterNetworkCallback();
        this.looping = false;
        if(socket != null && !socket.isClosed()) {
            socket.close();
        }
        activity.setAutoConnectorWorked();
    }

    /**
     * 给广播接收器准备的 关闭连接前更改显示
     */
    public void changeViewState() {
        activity.viewPagerAdapter.getHomeFragment().setAutoConnecting(false);
        activity.stateBarManager.addState(States.getStateList().get("info_auto_connect_not_wifi"));
    }

    public void setActivity(NewMainActivity activity) {
        this.activity = activity;
    }

    public boolean isWorking() {
        return this.looping;
    }
}
