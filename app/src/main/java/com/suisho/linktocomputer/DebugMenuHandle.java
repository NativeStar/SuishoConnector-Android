package com.suisho.linktocomputer;

import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.suisho.linktocomputer.activity.NewMainActivity;
import com.suisho.linktocomputer.constant.States;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugMenuHandle {
    private static final Logger logger = LoggerFactory.getLogger(DebugMenuHandle.class);

    public static void showDebugMenu(NewMainActivity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setItems(new CharSequence[]{
                        "Edit desktop client state",
                        "Finish activity",
                        "Throw exception",
                        "Edit state",
                }, (dialog, which) -> {
                    dialog.dismiss();
                    handle(which, activity);
                })
                .setTitle("Debug Menu")
                .setPositiveButton("Close", null)
                .show();
    }

    private static void handle(int which, NewMainActivity activity) {
        switch (which) {
            case 0:
                showEditDesktopClientStateDialog(activity);
                break;
            case 1:
                activity.finish();
                break;
            case 2:
                throw new RuntimeException("Test exception.Do not feedback this!");
            case 3:
                showEditLocalStateDialog(activity);
                break;
            default:
                logger.warn("Invalid debug menu item index: {}", which);
        }
    }

    private static void showEditDesktopClientStateDialog(NewMainActivity activity) {
        EditText editText = new EditText(activity);
        editText.setHint("State id");
        new MaterialAlertDialogBuilder(activity)
                .setView(editText)
                .setTitle("Edit desktop state")
                .setPositiveButton("Add", (dialog1, which1) -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("packetType", "edit_state");
                    jsonObject.addProperty("type", "add");
                    jsonObject.addProperty("name", editText.getText().toString());
                    GlobalVariables.computerConfigManager.getNetworkService().sendObject(jsonObject);
                })
                .setNegativeButton("Remove", (dialog1, which1) -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("packetType", "edit_state");
                    jsonObject.addProperty("type", "remove");
                    jsonObject.addProperty("name", editText.getText().toString());
                    GlobalVariables.computerConfigManager.getNetworkService().sendObject(jsonObject);
                })
                .setNeutralButton("Cancel", (dialog1, which1) -> {
                })
                .show();
    }

    private static void showEditLocalStateDialog(NewMainActivity activity) {
        EditText editText = new EditText(activity);
        editText.setHint("State id");
        new MaterialAlertDialogBuilder(activity)
                .setView(editText)
                .setTitle("Edit state")
                .setPositiveButton("Add", (dialog1, which1) -> activity.stateBarManager.addState(States.getStateList().get(editText.getText().toString())))
                .setNegativeButton("Remove", (dialog1, which1) -> activity.stateBarManager.removeState(States.getStateList().get(editText.getText().toString())))
                .setNeutralButton("Cancel", (dialog1, which1) -> {
                })
                .show();
    }
}
