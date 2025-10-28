package com.example.linktocomputer.instances;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.linktocomputer.R;
import com.example.linktocomputer.constant.States;
import com.example.linktocomputer.instances.adapter.StateListAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

@SuppressLint("RestrictedApi")
public class StateBarManager {
    private Activity activity;
    private ActionMenuItemView menuView;
    private final StateListAdapter adapter;
    private final AlertDialog stateDialog;
    private boolean showedTip=false;
    //在menuView没初始化时记录是否要刷新图标
    private boolean delaySetIcon=false;
    public StateBarManager(Activity activity) {
        this.activity = activity;
        adapter=new StateListAdapter(activity);
        View listView= LayoutInflater.from(activity).inflate(R.layout.state_list_dialog,null);
        MaterialAlertDialogBuilder dialogBuilder=new MaterialAlertDialogBuilder(activity);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);
        RecyclerView recyclerView= listView.findViewById(R.id.state_dialog_recycler_view);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        dialogBuilder.setView(listView);
        dialogBuilder.setPositiveButton(R.string.text_ok,null);
        dialogBuilder.setTitle("消息列表");
        stateDialog= dialogBuilder.create();
    }

    public void init() {
        if(menuView == null) {
            menuView = activity.findViewById(R.id.activity_state);
        }
        if(delaySetIcon) setIcon();
    }

    public void onMenuClick() {
        //这时候才能保证控件存在
        if(menuView == null) {
            menuView=activity.findViewById(R.id.activity_state);
            if(delaySetIcon) setIcon();
        }
        stateDialog.show();
    }

    private void showTipPopup() {
        if(menuView == null) return;
        View menuLayout = LayoutInflater.from(activity).inflate(R.layout.state_dialog, null, false);
        PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
        popupWindow.setTouchable(false);
        //activity可能被销毁
        if(!activity.isDestroyed()&&!activity.isFinishing())
            activity.runOnUiThread(()->popupWindow.showAsDropDown(menuView));

    }

    //activity被销毁后
    public void setActivity(Activity activity) {
        this.activity = activity;
        menuView = activity.findViewById(R.id.activity_state);
    }

    public void addState(States.State state) {
        if(!showedTip){
            showTipPopup();
            showedTip=true;
        }
        adapter.addState(state,true);
        setIcon();
    }

    public void removeState(String id) {
        adapter.removeState(id,true);
        setIcon();
    }
    public void removeState(States.State state){
        adapter.removeState(state,true);
        setIcon();
    }
    private void setIcon(){
        if(menuView==null){
            delaySetIcon=true;
            return;
        }
        activity.runOnUiThread(()->{
            switch (adapter.highestLevel){
                case BUSY:
                    menuView.setIcon(AppCompatResources.getDrawable(activity,R.drawable.baseline_hourglass_top_24));
                    break;
                case CHECKED:
                    menuView.setIcon(AppCompatResources.getDrawable(activity,R.drawable.baseline_check_24));
                    break;
                case INFO:
                    menuView.setIcon(AppCompatResources.getDrawable(activity,R.drawable.outline_info_24));
                    break;
                case WARN:
                    menuView.setIcon(AppCompatResources.getDrawable(activity,R.drawable.baseline_warning_amber_24));
                    break;
                case ERROR:
                    menuView.setIcon(AppCompatResources.getDrawable(activity,R.drawable.baseline_error_outline_24));
                    break;
            }
        });
    }
}
