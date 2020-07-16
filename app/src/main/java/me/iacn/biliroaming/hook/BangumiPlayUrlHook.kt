package me.iacn.biliroaming.hook

import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.log

/**
 * Created by iAcn on 2019/3/29
 * Email i@iacn.me
 */
class BangumiPlayUrlHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return
        log("startHook: BangumiPlayUrl")
    }
}