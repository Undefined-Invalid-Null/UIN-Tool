package com.UIN.Tool.ui.common

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.widget.EditText

object DialogHelper {

    fun confirmDialog(
        context: Context,
        title: String,
        message: String,
        onConfirm: DialogInterface.OnClickListener
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", onConfirm)
            .setNegativeButton("取消", null)
    }

    fun warningDialog(
        context: Context,
        title: String,
        message: String,
        onConfirm: DialogInterface.OnClickListener
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", onConfirm)
            .setNegativeButton("取消", null)
    }

    fun infoDialog(
        context: Context,
        title: String,
        message: String
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("好的", null)
            .create()
    }

    fun inputDialog(
        context: Context,
        title: String,
        hint: String,
        onConfirm: DialogInterface.OnClickListener
    ): AlertDialog.Builder {
        val input = EditText(context)
        input.hint = hint
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定", onConfirm)
            .setNegativeButton("取消", null)
    }

    fun listDialog(
        context: Context,
        title: String,
        items: Array<String>,
        onItemClick: DialogInterface.OnClickListener
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(items, onItemClick)
    }

    fun singleChoiceDialog(
        context: Context,
        title: String,
        items: Array<String>,
        checkedItem: Int,
        onItemClick: DialogInterface.OnClickListener
    ): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(items, checkedItem, onItemClick)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
    }

    fun progressDialog(
        context: Context,
        message: String
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setMessage(message)
            .setCancelable(false)
            .create()
            .apply { show() }
    }
}