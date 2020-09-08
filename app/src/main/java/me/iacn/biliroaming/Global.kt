package me.iacn.biliroaming

import android.util.Log

/**
 * Created by Meolunr on 2020/6/19
 * Email meolunr@gmail.com
 */
const val BILIBILI_PACKAGENAME = "tv.danmaku.bili"

fun Boolean.toIntString() = if (this) "1" else "0"

fun log(message: String) = Log.d("BiliRoaming", message)

fun MutableMap<String, String>.searchIfAbsent(key: String, block: () -> String): Boolean {
    if (!containsKey(key)) {
        val newValue = block()
        if (newValue.isNotEmpty()) {
            put(key, newValue)
            return true
        }
    }
    return false
}

fun MutableMap<String, String>.searchIfMultipleAbsent(vararg keys: String, block: () -> Array<String>): Boolean {
    var isUpdate = false
    for (key in keys) {
        if (!containsKey(key)) {
            val newValues = block()
            for ((index, value) in newValues.withIndex()) {
                if (value.isNotEmpty()) {
                    put(keys[index], value)
                    isUpdate = true
                }
            }
            break
        }
    }
    return isUpdate
}