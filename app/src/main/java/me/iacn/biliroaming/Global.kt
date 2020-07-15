package me.iacn.biliroaming

import android.util.Log

/**
 * Created by iAcn on 2020/6/19
 * Email i@iacn.me
 */
const val BILIBILI_PACKAGENAME = "tv.danmaku.bili"

fun Boolean.toIntString() = if (this) "1" else "0"

fun log(message: String) = Log.d("BiliRoaming", message)