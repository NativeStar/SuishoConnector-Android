package com.example.linktocomputer.instances.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linktocomputer.GlobalVariables;
import com.example.linktocomputer.R;
import com.example.linktocomputer.activity.NewMainActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class TrustedDeviceListAdapter extends RecyclerView.Adapter<TrustedDeviceListAdapter.DeviceHolder> {
    private final ArrayList<DeviceTrustInstance> deviceList;
    private final NewMainActivity context;
    private Runnable onDeviceListEmptyCallback;
    private final SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public TrustedDeviceListAdapter(NewMainActivity context, ArrayList<DeviceTrustInstance> deviceList) {
        this.deviceList=deviceList;
        this.context=context;
    }

    @NonNull
    @Override
    public DeviceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DeviceHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.trusted_device_manager_card,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceHolder holder, int position) {
        holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        //文本显示
        ((TextView)holder.itemView.findViewById(R.id.device_trust_manager_device_id)).setText(deviceList.get(position).name);
        ((TextView)holder.itemView.findViewById(R.id.device_trust_manager_last_connect_time)).setText("上次连接:"+simpleDateFormat.format(new Date(deviceList.get(position).lastConnectTime)));
        //按钮
        Button removeTrustButton=holder.itemView.findViewById(R.id.removeDeviceTrustButton);
        if(GlobalVariables.computerConfigManager!=null&&GlobalVariables.computerConfigManager.getId().equals(deviceList.get(position).id)&&context.isServerConnected()){
            //这是当前连接的设备 阻止操作
            removeTrustButton.setEnabled(false);
            removeTrustButton.setText(R.string.text_connected_device_remove_trust_button);
            return;
        }
        removeTrustButton.setOnClickListener(view->{
            String targetId=deviceList.get(position).id;
            SharedPreferences targetDeviceConfig=context.getSharedPreferences(targetId,Context.MODE_PRIVATE);
            targetDeviceConfig.edit().putBoolean("trustedDevice",false).apply();
            deviceList.remove(position);
            //如果删干净了将列表显示取消掉
            if(deviceList.isEmpty()&&this.onDeviceListEmptyCallback!=null){
                onDeviceListEmptyCallback.run();
            }
            if(!deviceList.isEmpty()){
                notifyItemRemoved(position);
            }
//            Toast.makeText(context, R.string.text_deleted, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    /**
     * 添加设备
     * @param deviceInstance 设备id
     */
    public void addDevice(DeviceTrustInstance deviceInstance){
        this.deviceList.add(deviceInstance);
    }

    /**
     * 移除设备
     * @param device 设备id
     */
    public void removeDevice(DeviceTrustInstance device){
        this.deviceList.remove(device);
    }
    public class DeviceHolder extends RecyclerView.ViewHolder{
        public DeviceHolder(@NonNull View itemView) {
            super(itemView);
         }
    }

    /**
     * 设备数据实例
     */
    public static class DeviceTrustInstance{
        private final String id;
        private long lastConnectTime;
        private final String name;

        /**
         *
         * @param id 设备id
         * @param lastConnectTime 上次连接时间戳
         */
        public DeviceTrustInstance(String name,String id,long lastConnectTime) {
            this.name=name;
            this.id=id;
            this.lastConnectTime=lastConnectTime;
        }
        public void updateLastConnectTime(long timestamp){
            this.lastConnectTime=timestamp;
        }

        public String getId() {
            return id;
        }
    }
    public void setOnDeviceListEmptyCallback(Runnable callback){
        this.onDeviceListEmptyCallback =callback;
    }
}
