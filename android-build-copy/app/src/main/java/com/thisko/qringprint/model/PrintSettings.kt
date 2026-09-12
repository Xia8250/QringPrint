package com.thisko.qringprint.model

import android.content.Context

private const val PREFS_NAME = "qring_print_settings"
private const val KEY_THICKNESS = "default_thickness"
private const val KEY_BAUD_RATE = "usb_baud_rate"

fun loadDefaultThickness(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_THICKNESS, 1)
        .coerceIn(1, 3)
}

fun saveDefaultThickness(context: Context, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_THICKNESS, value.coerceIn(1, 3))
        .apply()
}

fun loadUsbBaudRate(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_BAUD_RATE, 9600)
}

fun saveUsbBaudRate(context: Context, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_BAUD_RATE, value.coerceIn(1200, 3000000))
        .apply()
}
