package com.suisho.linktocomputer.interfaces;

import com.suisho.linktocomputer.abstracts.TransmitMessageAbstract;
import com.suisho.linktocomputer.activity.NewMainActivity;
import com.suisho.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.suisho.linktocomputer.fragment.TransmitFragment;

public interface IConnectedActivityMethods {
    void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract data, boolean requestSave);
    void showAlert(String title,String content,String buttonText);
    void showAlert(int title,int content,int buttonText);
    NewMainActivity getActivity();
    TransmitFragment getTransmitFragment();
    void showConnectingDialog();
    void closeConnectingDialog();
    void onConnected(String sessionId);
    void onDisconnect();
    void onNotificationListenerServiceConnect();
}
