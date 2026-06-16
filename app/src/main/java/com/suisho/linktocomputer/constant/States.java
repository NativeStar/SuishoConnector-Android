package com.suisho.linktocomputer.constant;

import com.suisho.linktocomputer.R;
import com.suisho.linktocomputer.enums.StateLevel;

import java.util.HashMap;

public class States {
    public static HashMap<String, State> StateList = new HashMap<>();

    public static class State {
        public final String id;
        public final int name;
        public int description;
        public final StateLevel level;
        public final boolean clickable;
        public final int icon;

        public State(String id, int nameId, int description, StateLevel level, boolean clickable) {
            this.id = id;
            this.name = nameId;
            this.description = description;
            this.level = level;
            this.clickable = clickable;
            this.icon = -1;
        }

        public State(String id, int nameId, int description, StateLevel level, boolean clickable, int icon) {
            this.id = id;
            this.name = nameId;
            this.description = description;
            this.level = level;
            this.clickable = clickable;
            this.icon = icon;
        }
    }

    public static HashMap<String, State> getStateList() {
        //TODO 也许可以优化下 类似js map
        if(StateList.isEmpty()) {
            //init
            //图标打包
            StateList.put("busy_packing_icon", new State("busy_packing_icon", R.string.state_name_icon_packing, R.string.state_desc_icon_packing, StateLevel.BUSY, false,R.drawable.baseline_archive_24));
            //电池优化
            StateList.put("info_battery_opt", new State("info_battery_opt", R.string.state_name_battery_opt, R.string.state_desc_battery_opt, StateLevel.INFO, true,R.drawable.baseline_battery_alert_24));
            //查询软件包列表权限
            StateList.put("warn_query_package_permission", new State("warn_query_package_permission", R.string.state_name_query_package_permission, R.string.state_desc_query_package_permission, StateLevel.WARN, true,R.drawable.baseline_list_24));
            //图标打包异常
            StateList.put("error_packing_icon", new State("error_packing_icon", R.string.state_name_error, R.string.state_desc_error_packing_icon, StateLevel.ERROR, false,R.drawable.baseline_no_backpack_24));
            //通知监听权限
            StateList.put("info_notification_listener_permission", new State("info_notification_listener_permission", R.string.state_name_notification_listener_permission, R.string.state_desc_notification_listener_permission, StateLevel.INFO, true,R.drawable.baseline_notification_important_24));
            //通知发送权限
            StateList.put("warn_send_notification", new State("warn_send_notification", R.string.state_name_send_notification, R.string.state_desc_send_notification, StateLevel.WARN, true,R.drawable.baseline_notifications_off_24));
            //自动连接异常
            StateList.put("error_auto_connect", new State("error_auto_connect", R.string.state_name_auto_connect_error, R.string.state_desc_auto_connect_error, StateLevel.ERROR, false,R.drawable.baseline_wifi_tethering_error_24));
            //自动连接关闭 非wifi
            StateList.put("info_auto_connect_not_wifi", new State("info_auto_connect_not_wifi", R.string.state_title_auto_connect_pause, R.string.state_desc_auto_connect_pause_not_wifi, StateLevel.INFO, false,R.drawable.baseline_wifi_off_24));
            //文件浏览服务器初始化异常
            StateList.put("error_phone_file_server", new State("error_phone_file_server", R.string.state_title_error_file_server, R.string.state_desc_error_file_server, StateLevel.ERROR, false));
            //有更新可用
            StateList.put("info_update_available", new State("info_update_available", R.string.state_title_update_available, R.string.state_desc_update_available, StateLevel.INFO, true,R.drawable.baseline_system_update_24));
            //PC端协议版本低
            StateList.put("warn_pc_protocol_version_low", new State("warn_pc_protocol_version_low", R.string.state_title_pc_protocol_version_low, R.string.state_desc_pc_protocol_version_low, StateLevel.WARN, false));
            //缺少勿扰模式权限 优先级不高
            StateList.put("info_not_interruption_filter_access_permission",new State("info_not_interruption_filter_access_permission", R.string.state_title_not_interruption_filter_access_permission, R.string.state_desc_not_interruption_filter_access_permission, StateLevel.INFO, true,R.drawable.baseline_do_not_disturb_off_24));
        }
        return StateList;
    }
}
