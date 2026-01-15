package com.suisho.linktocomputer.instances.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.suisho.linktocomputer.R;
import com.suisho.linktocomputer.constant.States;
import com.suisho.linktocomputer.enums.StateLevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StateListAdapter extends RecyclerView.Adapter {
    private final Activity activity;
    private final HashMap<String, States.State> newStates=new HashMap<>();
    //recyclerview喂不了hashmap
    private final List<States.State> renderList=new ArrayList<>();
    public StateLevel highestLevel=StateLevel.CHECKED;
    private final Logger logger = LoggerFactory.getLogger(StateListAdapter.class);

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.state_card, parent, false));
    }
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        States.State state=renderList.get(position);
        //根据等级设置图标
        ImageView imageView=holder.itemView.findViewById(R.id.state_dialog_card_icon);
        logger.debug("Init new state:{}",state.id);
        switch (state.level){
            case BUSY:
                imageView.setImageResource(R.drawable.baseline_hourglass_top_24);
                break;
            case CHECKED:
                imageView.setImageResource(R.drawable.baseline_check_24);
                break;
            case INFO:
                imageView.setImageResource(R.drawable.outline_info_24);
                break;
            case WARN:
                imageView.setImageResource(R.drawable.baseline_warning_amber_24);
                break;
            case ERROR:
                imageView.setImageResource(R.drawable.baseline_error_outline_24);
                break;
        }
        //可点击
        if(state.clickable){
            logger.debug("State '{}' clickable",state.id);
            holder.itemView.findViewById(R.id.state_dialog_card_clickable_icon).setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener((view)->{
                onCardClick(state);
            });
        }else{
            //避免操作错乱
            holder.itemView.findViewById(R.id.state_dialog_card_clickable_icon).setVisibility(View.GONE);
            holder.itemView.setClickable(false);
        }
        //描述文本
        ((TextView)holder.itemView.findViewById(R.id.state_dialog_card_name)).setText(activity.getText(state.name));
        ((TextView)holder.itemView.findViewById(R.id.state_dialog_card_description)).setText(activity.getText(state.description));
    }

    /**
     * 根据状态卡片类型执行点击事件
     * @param state 状态实例
     */
    private void onCardClick(States.State state){
        logger.debug("Clicked state card:{}",state.id);
        switch (state.id){
            case "info_battery_opt":
                @SuppressLint("BatteryLife")
                Intent batteryOptIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                batteryOptIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(batteryOptIntent);
                logger.debug("Open battery optimization permission activity");
                break;
            case "info_notification_listener_permission":
                Intent listener = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                listener.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(listener);
                logger.debug("Open notification listener permission activity");
                break;
            case "warn_send_notification":
                Intent intent = new Intent();
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("app_package", activity.getPackageName());
                intent.putExtra("app_uid",activity.getApplicationInfo().uid);
                intent.putExtra("android.provider.extra.APP_PACKAGE",activity.getPackageName());
                activity.startActivity(intent);
                logger.debug("Open notification permission activity");
                break;
            default:
                logger.warn("Unknown state card type:{}",state.id);
        }
    }
    @Override
    public int getItemCount() {
        return renderList.size();
    }

    public StateListAdapter(Activity activity) {
        this.activity=activity;
    }
    public void addState(States.State state, boolean refresh){
        logger.debug("Request add state:{}",state.id);
        if(newStates.containsKey(state.id)) return;
        newStates.put(state.id,state);
        if(refresh) refreshRenderList();
    }
    public void removeState(States.State state,boolean refresh){
        logger.debug("Request remove state by instance:{}",state.id);
        if(!newStates.containsKey(state.id)) return;
        newStates.remove(state.id);
        if(refresh) refreshRenderList();
    }
    public void removeState(String id,boolean refresh){
        logger.debug("Request remove state by id:{}",id);
        if(!newStates.containsKey(id)) return;
        newStates.remove(id);
        if(refresh) refreshRenderList();
    }
    private void refreshRenderList(){
        logger.debug("Refresh render list");
        //计算前重置状态
        highestLevel=StateLevel.CHECKED;
        renderList.clear();
        newStates.forEach((s, state) -> {
            if(state.level.compareTo(highestLevel) > 0){
                highestLevel=state.level;
            }
            renderList.add(state);
        });
        activity.runOnUiThread(()->notifyDataSetChanged());
    }
    private static class Holder extends RecyclerView.ViewHolder{
        public Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
