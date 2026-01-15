package com.suisho.linktocomputer.constant;

import com.suisho.linktocomputer.R;
import com.suisho.linktocomputer.enums.StateLevel;

import java.util.HashMap;

public class States {
    public static HashMap<String,State> StateList=new HashMap<>();
    public static class State {
        public final String id;
        public final int name;
        public int description;
        public final StateLevel level;
        public final boolean clickable;

        public State(String id,int nameId, int description, StateLevel level,boolean clickable) {
            this.id=id;
            this.name = nameId;
            this.description = description;
            this.level = level;
            this.clickable=clickable;
        }
    }
    public static HashMap<String,State> getStateList(){
        if(StateList.isEmpty()){
            //init
            //图标打包
            StateList.put("busy_packing_icon",new State("busy_packing_icon",R.string.state_name_icon_packing, R.string.state_desc_icon_packing, StateLevel.BUSY,false));
            //电池优化
            StateList.put("info_battery_opt",new State("info_battery_opt",R.string.state_name_battery_opt,R.string.state_desc_battery_opt,StateLevel.INFO,true));
            //查询软件包列表权限
            StateList.put("warn_query_package_permission",new State("warn_query_package_permission",R.string.state_name_query_package_permission,R.string.state_desc_query_package_permission,StateLevel.WARN,false));
            //图标打包异常
            StateList.put("error_packing_icon",new State("error_packing_icon",R.string.state_name_error,R.string.state_desc_error_packing_icon,StateLevel.ERROR,false));
            //通知监听权限
            StateList.put("info_notification_listener_permission",new State("info_notification_listener_permission",R.string.state_name_notification_listener_permission,R.string.state_desc_notification_listener_permission,StateLevel.INFO,true));
            //通知发送权限
            StateList.put("warn_send_notification",new State("warn_send_notification",R.string.state_name_send_notification,R.string.state_desc_send_notification,StateLevel.WARN,true));
            //自动连接异常
            StateList.put("error_auto_connect",new State("error_auto_connect",R.string.state_name_auto_connect_error,R.string.state_desc_auto_connect_error,StateLevel.ERROR,false));
            //自动连接关闭 非wifi
            StateList.put("info_auto_connect_not_wifi",new State("info_auto_connect_not_wifi",R.string.state_title_auto_connect_pause,R.string.state_desc_auto_connect_pause_not_wifi,StateLevel.INFO,false));
            //文件浏览服务器初始化异常
            StateList.put("error_phone_file_server",new State("error_phone_file_server",R.string.state_title_error_file_server,R.string.state_desc_error_file_server,StateLevel.ERROR,false));
        }
        return StateList;
    }
}
