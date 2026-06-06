// app/src/main/java/com/UIN/Tool/ui/common/DialogHelper.java
package com.UIN.Tool.ui.common;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

import androidx.annotation.StringRes;

import com.UIN.Tool.R;

/**
 * 对话框辅助类
 * 统一创建各种对话框，消除代码重复
 */
public class DialogHelper {

    /**
     * 创建确认对话框
     */
    public static AlertDialog.Builder confirmDialog(Context context, String title, String message,
                                                     DialogInterface.OnClickListener onConfirm) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, onConfirm)
                .setNegativeButton(R.string.cancel, null);
    }

    /**
     * 创建警告对话框
     */
    public static AlertDialog.Builder warningDialog(Context context, String title, String message,
                                                    DialogInterface.OnClickListener onConfirm) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, onConfirm)
                .setNegativeButton(R.string.cancel, null);
    }

    /**
     * 创建信息对话框
     */
    public static AlertDialog.Builder infoDialog(Context context, String title, String message) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null);
    }

    /**
     * 创建带输入框的对话框
     */
    public static AlertDialog.Builder inputDialog(Context context, String title, String hint,
                                                   DialogInterface.OnClickListener onConfirm) {
        EditText input = new EditText(context);
        input.setHint(hint);
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.confirm, onConfirm)
                .setNegativeButton(R.string.cancel, null);
    }

    /**
     * 创建列表选择对话框
     */
    public static AlertDialog.Builder listDialog(Context context, String title, String[] items,
                                                  DialogInterface.OnClickListener onItemClick) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(items, onItemClick);
    }

    /**
     * 创建单选对话框
     */
    public static AlertDialog.Builder singleChoiceDialog(Context context, String title, String[] items,
                                                          int checkedItem, DialogInterface.OnClickListener onItemClick) {
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(items, checkedItem, onItemClick)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null);
    }

    /**
     * 创建进度对话框
     */
    public static AlertDialog progressDialog(Context context, String message) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(message)
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }
}