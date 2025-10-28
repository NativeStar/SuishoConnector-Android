package com.example.linktocomputer;

import android.content.SharedPreferences;

import com.example.linktocomputer.instances.ComputerConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;

public class GlobalVariables {
//    public static Messenger transmitSendMessenger=null;
    public static boolean networkServiceBound =false;
    public static String androidId;
    public static String serverAddress;
    public static final Gson jsonBuilder=new GsonBuilder().create();
    public static String computerName="未连接";
    public static String computerId="未连接";
    public static SharedPreferences settings=null;
    public static SharedPreferences preferences=null;
    public static HashMap<String,String> appPackageNameMapper= new HashMap<>();
    public static ComputerConfigManager computerConfigManager;
}
