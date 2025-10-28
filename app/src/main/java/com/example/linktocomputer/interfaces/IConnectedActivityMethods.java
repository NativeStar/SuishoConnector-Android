package com.example.linktocomputer.interfaces;

import com.example.linktocomputer.abstracts.TransmitMessageAbstract;
import com.example.linktocomputer.activity.NewMainActivity;
import com.example.linktocomputer.enums.TransmitRecyclerAddItemType;
import com.example.linktocomputer.fragment.TransmitFragment;

public interface IConnectedActivityMethods {
    void addItem(TransmitRecyclerAddItemType type, TransmitMessageAbstract data, boolean requestSave);
    void showAlert(String title,String content,String buttonText);
    void showAlert(int title,int content,int buttonText);
    NewMainActivity getActivity();
    TransmitFragment getTransmitFragment();
//    void showSnackBar();
    void showConnectingDialog();
    void closeConnectingDialog();
    void onConnected(String sessionId);
    void onDisconnect();
    void onNotificationListenerServiceConnect();
    void requestMediaProjectionPermission();
}
